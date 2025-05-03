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
        ProjectConfig[] configs = ProjectConfigLoader.load();

        Properties ghProps = new Properties();
        try (InputStream in = MantiMetrics.class.getResourceAsStream("/github.properties")) {
            if (in == null) throw new IllegalStateException("Missing github.properties");
            ghProps.load(in);
        }
        String githubPat = ghProps.getProperty("github.pat");
        if (githubPat == null || githubPat.isBlank())
            throw new IllegalStateException("github.pat not set");

        GitService gitService         = new GitService(githubPat);
        CodeParser parser             = new CodeParser(gitService);
        MetricsCalculator metricsCalc = new MetricsCalculator();
        JiraClient jira               = new JiraClient();
        CSVWriter csvWriter           = new CSVWriter();

        for (ProjectConfig cfg : configs) {
            String owner = "apache";
            String repo  = cfg.getName().toLowerCase();

            List<MethodData> allMethods = parser.parseAndComputeOnline(
                    owner, repo, metricsCalc
            );

            jira.initialize(cfg.getJiraProjectKey());
            List<String> bugKeys = jira.fetchBugKeys();
            allMethods.forEach(md ->
                    md.setBuggy(jira.isMethodBuggy(md.getCommitHashes(), bugKeys))
            );

            String outCsv = cfg.getName() + "_dataset.csv";
            csvWriter.write(outCsv, allMethods);
            System.out.println("Generated CSV for project "
                    + cfg.getName() + ": " + outCsv);
        }
    }
}