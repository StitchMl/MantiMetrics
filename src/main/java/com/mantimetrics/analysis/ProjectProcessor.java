package com.mantimetrics.analysis;

import com.mantimetrics.csv.CSVWriter;
import com.mantimetrics.csv.CsvWriteException;
import com.mantimetrics.dataset.DatasetArtifactService;
import com.mantimetrics.git.GitService;
import com.mantimetrics.git.ProjectConfig;
import com.mantimetrics.jira.JiraClientException;
import com.mantimetrics.pmd.PmdAnalyzer;
import com.mantimetrics.release.ReleaseProcessingException;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ProjectProcessor {
    private final ProjectReleasePlanner releasePlanner;
    private final ReleaseExecutionService releaseExecutionService;
    private final GitService gitService;
    private final CSVWriter csvWriter;
    private final PmdAnalyzer pmdAnalyzer;
    private final DatasetArtifactService datasetArtifactService;

    public ProjectProcessor(
            ProjectReleasePlanner releasePlanner,
            ReleaseExecutionService releaseExecutionService,
            GitService gitService,
            CSVWriter csvWriter,
            PmdAnalyzer pmdAnalyzer,
            DatasetArtifactService datasetArtifactService
    ) {
        this.releasePlanner = releasePlanner;
        this.releaseExecutionService = releaseExecutionService;
        this.gitService = gitService;
        this.csvWriter = csvWriter;
        this.pmdAnalyzer = pmdAnalyzer;
        this.datasetArtifactService = datasetArtifactService;
    }

    public void process(ProjectConfig config, List<Granularity> granularities)
            throws JiraClientException, CsvWriteException {
        ProjectReleasePlan plan = releasePlanner.plan(config);
        if (plan == null) {
            return;
        }

        Map<Granularity, Path> csvPaths = new LinkedHashMap<>();
        List<ProjectContext> contexts = openContexts(plan, granularities, csvPaths);
        try {
            for (int index = 0; index < plan.selectedTags().size(); index++) {
                String tag = plan.selectedTags().get(index);
                String prevTag = index > 0 ? plan.selectedTags().get(index - 1) : null;
                releaseExecutionService.processRelease(tag, prevTag, contexts);
            }
        } finally {
            closeContexts(contexts);
        }

        generateArtifacts(csvPaths);
    }

    private List<ProjectContext> openContexts(
            ProjectReleasePlan plan,
            List<Granularity> granularities,
            Map<Granularity, Path> csvPaths
    ) throws CsvWriteException {
        List<ProjectContext> contexts = new ArrayList<>();
        try {
            for (Granularity granularity : granularities) {
                Path csvPath = Paths.get("output", plan.repo() + "_dataset_" + granularity.name().toLowerCase() + ".csv");
                BufferedWriter writer = csvWriter.open(csvPath, granularity);
                csvPaths.put(granularity, csvPath);
                contexts.add(new ProjectContext(
                        plan.owner(),
                        plan.repo(),
                        granularity,
                        gitService,
                        pmdAnalyzer,
                        csvWriter,
                        new HashMap<>(),
                        plan.bugKeys(),
                        writer
                ));
            }
            return contexts;
        } catch (CsvWriteException exception) {
            try {
                closeContexts(contexts);
            } catch (CsvWriteException closeFailure) {
                exception.addSuppressed(closeFailure);
            }
            throw exception;
        }
    }

    private void generateArtifacts(Map<Granularity, Path> csvPaths) {
        for (Path csvPath : csvPaths.values()) {
            try {
                datasetArtifactService.generate(csvPath);
            } catch (IOException exception) {
                throw new ReleaseProcessingException("I/O error when generating derived dataset artifacts", exception);
            }
        }
    }

    private void closeContexts(List<ProjectContext> contexts) throws CsvWriteException {
        CsvWriteException failure = null;
        for (ProjectContext context : contexts) {
            try {
                context.writer().close();
            } catch (IOException exception) {
                if (failure == null) {
                    failure = new CsvWriteException("Failed to close CSV writer", exception);
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
    }
}
