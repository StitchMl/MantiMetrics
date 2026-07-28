package com.mantimetrics.orchestrator;

import com.mantimetrics.releaseSelection.ReleaseTimelineJiraGit;
import com.mantimetrics.datasetOutput.MilestoneAuditWriter;
import com.mantimetrics.projectSelector.OptionsSelector;
import com.mantimetrics.projectSelector.ProjectSelection;
import com.mantimetrics.config.ConfigException;
import com.mantimetrics.config.GitTokenLoader;
import com.mantimetrics.config.ConfigLoader;
import com.mantimetrics.config.SonarTokenLoader;
import com.mantimetrics.datasetOutput.CSVWriter;
import com.mantimetrics.datasetOutput.CSVException;
import com.mantimetrics.jira.JiraClientException;
import com.mantimetrics.datasetOutput.ARFFWriter;
import com.mantimetrics.datasetOutput.DatasetArtifactGenerator;
import com.mantimetrics.datasetOutput.CSVReader;
import com.mantimetrics.datasetOutput.MetadataWriter;
import com.mantimetrics.datasetOutput.DatasetTableWriter;
import com.mantimetrics.datasetM3.WhatIfDatasetBuilder;
import com.mantimetrics.git.GitFacade;
import com.mantimetrics.git.GitConfig;
import com.mantimetrics.gitIssue.GitIssueClient;
import com.mantimetrics.jira.JiraFacade;
import com.mantimetrics.feature.MetricsCalculator;
import com.mantimetrics.javaParsing.JavaSourceParser;
import com.mantimetrics.releaseSelection.ReleaseSnoringFilter;
import com.mantimetrics.smell.SonarClient;
import com.mantimetrics.smell.SonarPreScanOrchestrator;
import com.mantimetrics.utility.TmpDirCleaner;

import java.io.IOException;

/**
 * Application composition root. It wires the concrete services once and then delegates the actual work
 * to the release-processing pipeline.
 */
@SuppressWarnings("GrazieInspectionRunner")
public final class StartAnalysis {
    private final GitTokenLoader gitHubTokenLoader = new GitTokenLoader();
    private final SonarTokenLoader sonarTokenLoader = new SonarTokenLoader();
    @SuppressWarnings("java:S106")
    private final ProjectSelection projectSelectionPrompt = new ProjectSelection(System.in, System.out);

    /**
     * Wires the runtime services, resolves the target project and executes the analysis pipeline.
     *
     * @param cliOptions command-line options resolved at startup
     */
    public void run(OptionsSelector cliOptions) throws IOException, ConfigException, JiraClientException, CSVException {
        String githubToken = loadGithubToken();
        GitFacade gitService = new GitFacade(githubToken);
        try {
            Orchestrator processor = createProcessor(gitService, new GitIssueClient(githubToken));
            GitConfig[] configs = resolveProjectConfigs(cliOptions);
            for (GitConfig config : configs) {
                processor.run(config);
            }
        } finally {
            TmpDirCleaner.cleanup(gitService.getTmp());
        }
    }

    /**
     * Builds the concrete processing pipeline while keeping each service narrowly focused.
     *
     * @param gitService Git service shared by the analysis pipeline
     * @return fully wired project processor
     */
    private Orchestrator createProcessor(GitFacade gitService, GitIssueClient gitIssueClient) {
        JiraFacade jiraClient = new JiraFacade();
        JavaSourceParser codeParser = new JavaSourceParser(gitService);
        String sonarToken = sonarTokenLoader.load(MainApp.class);
        SonarClient sonarClient = new SonarClient(sonarToken);

        return new Orchestrator(
                new ReleaseTimelineJiraGit(gitService, new ReleaseSnoringFilter(), jiraClient, gitIssueClient),
                new SingleReleaseExecution(codeParser,
                        new ReleaseToDataset(codeParser, new MetricsCalculator())),
                gitService,
                new CSVWriter(),
                new SonarPreScanOrchestrator(gitService, sonarClient, sonarToken),
                sonarClient,
                new OutputServices(
                        new DatasetArtifactGenerator(
                                new CSVReader(),
                                new DatasetTableWriter(),
                                new ARFFWriter(),
                                new MetadataWriter(),
                                new WhatIfDatasetBuilder()
                        ),
                        new MilestoneAuditWriter(new CSVReader())
                )
        );
    }

    /**
     * Resolves the project to analyze either from the CLI or from the interactive prompt.
     *
     * @param cliOptions command-line options resolved at startup
     * @return single-element array containing the selected project configuration
     */
    @SuppressWarnings("GrazieInspectionRunner")
    private GitConfig[] resolveProjectConfigs(OptionsSelector cliOptions)
            throws ConfigException, IOException {
        if (cliOptions.hasCliProject()) {
            return new GitConfig[] { cliOptions.cliProject() };
        }
        return new GitConfig[] { projectSelectionPrompt.prompt(ConfigLoader.load()) };
    }

    /**
     * Loads the GitHub token required by the GitHub-backed services.
     *
     * @return configured GitHub personal access token
     * @throws IOException when the token cannot be resolved from configuration
     */
    private String loadGithubToken() throws IOException {
        return gitHubTokenLoader.load(MainApp.class);
    }
}
