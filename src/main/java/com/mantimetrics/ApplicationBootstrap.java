package com.mantimetrics;

import com.mantimetrics.analysis.ProjectProcessor;
import com.mantimetrics.analysis.ProjectReleasePlanner;
import com.mantimetrics.analysis.ReleaseDatasetCollector;
import com.mantimetrics.analysis.ReleaseExecutionService;
import com.mantimetrics.config.GitHubTokenLoader;
import com.mantimetrics.config.ProjectConfigLoader;
import com.mantimetrics.csv.CSVWriter;
import com.mantimetrics.dataset.DatasetArffWriter;
import com.mantimetrics.dataset.DatasetArtifactService;
import com.mantimetrics.dataset.DatasetCsvTableReader;
import com.mantimetrics.dataset.DatasetMetadataWriter;
import com.mantimetrics.dataset.DatasetTableWriter;
import com.mantimetrics.dataset.WhatIfDatasetBuilder;
import com.mantimetrics.git.GitService;
import com.mantimetrics.git.ProjectConfig;
import com.mantimetrics.jira.JiraClient;
import com.mantimetrics.metrics.MetricsCalculator;
import com.mantimetrics.parser.CodeParser;
import com.mantimetrics.pmd.PmdAnalyzer;
import com.mantimetrics.release.ReleaseSelector;
import com.mantimetrics.util.TempDirectoryCleaner;

import java.io.IOException;

final class ApplicationBootstrap {
    private final GitHubTokenLoader gitHubTokenLoader = new GitHubTokenLoader();
    private final ProjectSelectionPrompt projectSelectionPrompt = new ProjectSelectionPrompt(System.in, System.out);

    void run(CliOptions cliOptions) throws Exception {
        GitService gitService = new GitService(loadGithubToken());
        try {
            ProjectProcessor processor = createProcessor(gitService);
            ProjectConfig[] configs = resolveProjectConfigs(cliOptions);
            for (ProjectConfig config : configs) {
                processor.process(config, cliOptions.granularityOption().granularities());
            }
        } finally {
            TempDirectoryCleaner.cleanup(gitService.getTmp());
        }
    }

    private ProjectProcessor createProcessor(GitService gitService) {
        JiraClient jiraClient = new JiraClient();
        CodeParser codeParser = new CodeParser(gitService);

        return new ProjectProcessor(
                new ProjectReleasePlanner(gitService, new ReleaseSelector(), jiraClient),
                new ReleaseExecutionService(codeParser,
                        new ReleaseDatasetCollector(codeParser, new MetricsCalculator(), jiraClient)),
                gitService,
                new CSVWriter(),
                new PmdAnalyzer(),
                new DatasetArtifactService(
                        new DatasetCsvTableReader(),
                        new DatasetTableWriter(),
                        new DatasetArffWriter(),
                        new DatasetMetadataWriter(),
                        new WhatIfDatasetBuilder()
                )
        );
    }

    private ProjectConfig[] resolveProjectConfigs(CliOptions cliOptions) throws Exception {
        if (cliOptions.hasCliProject()) {
            return new ProjectConfig[] { cliOptions.cliProject() };
        }
        return new ProjectConfig[] { projectSelectionPrompt.prompt(ProjectConfigLoader.load()) };
    }

    private String loadGithubToken() throws IOException {
        return gitHubTokenLoader.load(MantiMetrics.class);
    }
}
