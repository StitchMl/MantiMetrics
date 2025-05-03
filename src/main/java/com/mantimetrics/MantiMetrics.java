package com.mantimetrics;

import com.mantimetrics.config.ProjectConfigLoader;
import com.mantimetrics.git.GitService;
import com.mantimetrics.parser.CodeParser;
import com.mantimetrics.metrics.MetricsCalculator;
import com.mantimetrics.jira.JiraClient;
import com.mantimetrics.csv.CSVWriter;
import com.mantimetrics.model.MethodData;
import com.mantimetrics.git.ProjectConfig;

import java.io.InputStream;
import java.util.List;
import java.util.Properties;

public class MantiMetrics {
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

        // Initialize services
        GitService gitService         = new GitService(githubPat);
        CodeParser parser             = new CodeParser(gitService);
        MetricsCalculator metricsCalc = new MetricsCalculator();
        JiraClient jira               = new JiraClient();
        CSVWriter csvWriter           = new CSVWriter();

        // Process each project
        for (ProjectConfig cfg : configs) {
            String owner = "apache";
            String repo  = cfg.getName().toLowerCase();

            // 1) Fetch & analyze methods online
            List<MethodData> allMethods = parser.parseAndComputeOnline(owner, repo, metricsCalc);

            // 2) Label buggy methods via JIRA
            jira.initialize(cfg.getJiraProjectKey());
            List<String> bugKeys = jira.fetchBugKeys();
            allMethods.forEach(md ->
                    md.setBuggy(jira.isMethodBuggy(md.getCommitHashes(), bugKeys))
            );

            // 3) Export to CSV
            String outCsv = cfg.getName() + "_dataset.csv";
            csvWriter.write(outCsv, allMethods);
            System.out.println("Generated CSV for project "
                    + cfg.getName() + ": " + outCsv);
        }
    }
}