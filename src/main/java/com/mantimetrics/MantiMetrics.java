package com.mantimetrics;

import com.mantimetrics.config.ProjectConfigLoader;
import com.mantimetrics.git.GitService;
import com.mantimetrics.parser.CodeParser;
import com.mantimetrics.metrics.MetricsCalculator;
import com.mantimetrics.jira.JiraClient;
import com.mantimetrics.csv.CSVWriter;
import com.mantimetrics.model.MethodData;
import com.mantimetrics.git.ProjectConfig;
import com.mantimetrics.release.ReleaseSelector;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

public class MantiMetrics {
    static Logger logger = Logger.getLogger(MantiMetrics.class.getName());

    public static void main(String[] args) throws Exception {
        // Load project configurations
        ProjectConfig[] configs = ProjectConfigLoader.load();

        // Load GitHub PAT from properties
        Properties ghProps = new Properties();
        try (InputStream in = MantiMetrics.class.getResourceAsStream("/github.properties")) {
            if (in == null) {
                throw new IllegalStateException("Cannot find github.properties in resources");
            }
            ghProps.load(in);
        }
        String githubPat = ghProps.getProperty("github.pat");
        if (githubPat == null || githubPat.isBlank()) {
            throw new IllegalStateException("The github.properties file must contain github.pat");
        }
        logger.info("GitHub PAT loaded successfully.");

        // Initialize services
        GitService gitService         = new GitService(githubPat);
        CodeParser parser             = new CodeParser(gitService);
        MetricsCalculator metricsCalc = new MetricsCalculator();
        JiraClient jira               = new JiraClient();
        ReleaseSelector selector      = new ReleaseSelector();
        CSVWriter csvWriter           = new CSVWriter();

        // Process each project
        for (ProjectConfig cfg : configs) {
            String owner = cfg.getOwner();
            String repo  = cfg.getName().toLowerCase();

            // 1) estrai tutte le tag e seleziona la percentuale indicata
            if (logger.isLoggable(Level.INFO)) {
                logger.log(Level.INFO,
                        "Fetching and analyzing methods for project: {0}",
                        new Object[]{cfg.getName() });
            }
            List<String> tags     = gitService.listTags(owner, repo);
            if (logger.isLoggable(Level.INFO)) {
                logger.log(Level.INFO,
                        "Found {0} tags for project {1}",
                        new Object[]{ tags.size(), cfg.getName() });
            }
            if (logger.isLoggable(Level.INFO)) {
                logger.log(Level.INFO,
                        "Selecting first {0}% of tags for project {1}",
                        new Object[]{ cfg.getPercentage(), cfg.getName() });
            }

            List<String> selected = selector.selectFirstPercent(tags, cfg.getPercentage());

            List<MethodData> allMethods = new ArrayList<>();
            for (String tag : selected) {
                if (logger.isLoggable(Level.INFO)) {
                    logger.log(Level.INFO,
                            "Analysing tag {0}",
                            new Object[]{ tag });
                }

                allMethods.addAll(
                        parser.parseAndComputeOnline(owner, repo, tag, metricsCalc)
                );
            }

            // 2) Label buggy methods via JIRA
            if (logger.isLoggable(Level.INFO)) {
                logger.log(Level.INFO,
                        "Labeling buggy methods for project: {0}",
                        new Object[]{ cfg.getName() });
            }
            jira.initialize(cfg.getJiraProjectKey());
            List<String> bugKeys = jira.fetchBugKeys();
            if (logger.isLoggable(Level.INFO)) {
                logger.log(Level.INFO,
                        "Found {0} JIRA issues for project {1}",
                        new Object[]{ bugKeys.size(), cfg.getName() });
            }
            allMethods.forEach(md ->
                    md.setBuggy(jira.isMethodBuggy(md.getCommitHashes(), bugKeys))
            );

            // 3) Export to CSV
            if (logger.isLoggable(Level.INFO)) {
                logger.log(Level.INFO,
                        "Exporting data to CSV for project: {0}",
                        new Object[]{ cfg.getName() });
            }
            String outCsv = "output/" + cfg.getName().toLowerCase() + "_dataset.csv";
            csvWriter.write(outCsv, allMethods);
            if (logger.isLoggable(Level.INFO)) {
                logger.log(Level.INFO,
                        "Generated CSV for project {0}: {1}",
                        new Object[]{ cfg.getName(), outCsv });
            }
        }
    }
}