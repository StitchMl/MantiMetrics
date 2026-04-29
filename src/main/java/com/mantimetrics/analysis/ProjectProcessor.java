package com.mantimetrics.analysis;

import com.mantimetrics.csv.CSVWriter;
import com.mantimetrics.csv.CsvWriteException;
import com.mantimetrics.dataset.DatasetArtifactService;
import com.mantimetrics.git.GitService;
import com.mantimetrics.git.ProjectConfig;
import com.mantimetrics.history.RowHistoryStore;
import com.mantimetrics.jira.JiraClientException;
import com.mantimetrics.labeling.HistoricalBugLabelIndex;
import com.mantimetrics.labeling.HistoricalBugLabelIndexBuilder;
import com.mantimetrics.audit.MilestoneAuditService;
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

/**
 * Project-level orchestrator. It preloads the full release history once, builds the historical bug oracle,
 * then writes one raw dataset per requested granularity.
 */
public final class ProjectProcessor {
    private final ProjectReleasePlanner releasePlanner;
    private final ReleaseExecutionService releaseExecutionService;
    private final GitService gitService;
    private final CSVWriter csvWriter;
    private final PmdAnalyzer pmdAnalyzer;
    private final DatasetArtifactService datasetArtifactService;
    private final MilestoneAuditService milestoneAuditService;

    /**
     * Creates the project processor with all collaborators needed to execute the full release pipeline.
     *
     * @param releasePlanner planner that resolves the common release timeline
     * @param releaseExecutionService service that executes one prepared release
     * @param gitService Git service used to preload release commit data
     * @param csvWriter CSV writer used to open per-granularity output files
     * @param pmdAnalyzer PMD analyzer reused across releases
     * @param datasetArtifactService service that generates derived dataset artifacts
     * @param milestoneAuditService service that writes the milestone audit JSON
     */
    public ProjectProcessor(
            ProjectReleasePlanner releasePlanner,
            ReleaseExecutionService releaseExecutionService,
            GitService gitService,
            CSVWriter csvWriter,
            PmdAnalyzer pmdAnalyzer,
            DatasetArtifactService datasetArtifactService,
            MilestoneAuditService milestoneAuditService
    ) {
        this.releasePlanner = releasePlanner;
        this.releaseExecutionService = releaseExecutionService;
        this.gitService = gitService;
        this.csvWriter = csvWriter;
        this.pmdAnalyzer = pmdAnalyzer;
        this.datasetArtifactService = datasetArtifactService;
        this.milestoneAuditService = milestoneAuditService;
    }

    /**
     * Executes the end-to-end analysis for one project and all requested granularities.
     *
     * @param config project configuration to analyze
     * @param granularities dataset granularities to generate
     * @throws JiraClientException when Jira metadata cannot be loaded
     * @throws CsvWriteException when a dataset CSV file cannot be written or closed
     */
    public void process(ProjectConfig config, List<Granularity> granularities)
            throws JiraClientException, CsvWriteException {
        ProjectReleasePlan plan = releasePlanner.plan(config);
        if (plan == null) {
            return;
        }

        List<ReleaseSnapshot> releaseHistory = buildReleaseHistory(plan);
        HistoricalBugLabelIndex labelIndex = new HistoricalBugLabelIndexBuilder()
                .build(plan.timeline(), plan.selectedTags(), plan.resolvedTickets(), releaseHistory);
        Map<Granularity, Path> csvPaths = new LinkedHashMap<>();
        List<ProjectContext> contexts = openContexts(plan, granularities, csvPaths, labelIndex);
        try {
            for (ReleaseSnapshot snapshot : releaseHistory) {
                if (!plan.selectedTags().contains(snapshot.tag())) {
                    continue;
                }
                releaseExecutionService.processRelease(snapshot, contexts);
            }
        } finally {
            closeContexts(contexts);
        }

        generateArtifacts(csvPaths, plan, labelIndex, releaseHistory);
    }

    /**
     * Opens one CSV writer and one independent history state per granularity so class-level and method-level
     * analyses can coexist without sharing mutable state.
     */
    private List<ProjectContext> openContexts(
            ProjectReleasePlan plan,
            List<Granularity> granularities,
            Map<Granularity, Path> csvPaths,
            HistoricalBugLabelIndex labelIndex
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
                        pmdAnalyzer,
                        csvWriter,
                        new HashMap<>(),
                        new RowHistoryStore(),
                        labelIndex,
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

    /**
     * Preloads the complete release history, including the releases excluded by snoring, because those future
     * fix commits are still needed to label the older dataset rows.
     */
    private List<ReleaseSnapshot> buildReleaseHistory(ProjectReleasePlan plan) {
        List<ReleaseSnapshot> history = new ArrayList<>();
        List<String> timelineTags = plan.timeline().orderedTags();
        for (int index = 0; index < timelineTags.size(); index++) {
            String tag = timelineTags.get(index);
            String previousTag = index > 0 ? timelineTags.get(index - 1) : null;
            try {
                history.add(new ReleaseSnapshot(
                        tag,
                        previousTag,
                        gitService.buildReleaseCommitData(plan.owner(), plan.repo(), previousTag, tag)
                ));
            } catch (IOException exception) {
                throw new ReleaseProcessingException("I/O error while preloading commit history for " + tag, exception);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new ReleaseProcessingException("Interrupted while preloading commit history for " + tag, exception);
            }
        }
        return history;
    }

    /**
     * Generates the derived artifacts and audit file for every produced raw dataset.
     *
     * @param csvPaths raw dataset paths grouped by granularity
     * @param plan release plan associated with the project
     * @param labelIndex historical bug-label index used during labeling
     */
    private void generateArtifacts(
            Map<Granularity, Path> csvPaths,
            ProjectReleasePlan plan,
            HistoricalBugLabelIndex labelIndex,
            List<ReleaseSnapshot> releaseHistory
    ) {
        double linkageRate = computeLinkageRate(releaseHistory);
        for (Path csvPath : csvPaths.values()) {
            try {
                datasetArtifactService.generate(csvPath);
                milestoneAuditService.write(
                        csvPath,
                        plan.timeline().size(),
                        plan.selectedTags().size(),
                        labelIndex.summary(),
                        linkageRate
                );
            } catch (IOException exception) {
                throw new ReleaseProcessingException("I/O error when generating derived dataset artifacts", exception);
            }
        }
    }

    /**
     * Computes the project-level linkage rate as the proportion of unique commits (touching at least one
     * Java file) that carry a Jira issue key, aggregated across the full release history.
     *
     * @param releaseHistory complete list of preloaded release snapshots
     * @return linkage rate in [0.0, 1.0], or 0.0 when no Java commits are found
     */
    private double computeLinkageRate(List<ReleaseSnapshot> releaseHistory) {
        long totalJava = releaseHistory.stream()
                .mapToLong(s -> s.commitData().totalJavaCommits())
                .sum();
        long linkedJava = releaseHistory.stream()
                .mapToLong(s -> s.commitData().issueLinkedJavaCommits())
                .sum();
        return totalJava == 0 ? 0.0 : (double) linkedJava / totalJava;
    }

    /**
     * Closes every open CSV writer, aggregating the first close failure when needed.
     *
     * @param contexts project contexts holding the open writers
     * @throws CsvWriteException when at least one writer cannot be closed
     */
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
