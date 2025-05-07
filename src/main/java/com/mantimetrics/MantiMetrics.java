package com.mantimetrics;

import com.mantimetrics.config.ProjectConfigLoader;
import com.mantimetrics.git.GitService;
import com.mantimetrics.jira.JiraClient;
import com.mantimetrics.parser.CodeParser;
import com.mantimetrics.metrics.MetricsCalculator;
import com.mantimetrics.csv.CSVWriter;
import com.mantimetrics.model.MethodData;
import com.mantimetrics.git.ProjectConfig;
import com.mantimetrics.release.ReleaseSelector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

public class MantiMetrics {
    private static final Logger logger = LoggerFactory.getLogger(MantiMetrics.class);

    public static void main(String[] args) throws Exception {
        // 1) load config
        logger.info("Loading project configurations");
        ProjectConfig[] configs = ProjectConfigLoader.load();

        // 2) GitHub PAT
        Properties ghProps = new Properties();
        try (InputStream in = MantiMetrics.class.getResourceAsStream("/github.properties")) {
            if (in == null) {
                logger.error("github.properties missing");
                throw new IllegalStateException("github.properties missing");
            }
            ghProps.load(in);
        }
        String githubPat = ghProps.getProperty("github.pat");
        if (githubPat == null || githubPat.isBlank()) {
            logger.error("github.pat missing");
            throw new IllegalStateException("github.pat missing");
        }
        logger.info("GitHub PAT loaded");

        // 3) init services
        GitService gitService         = new GitService(githubPat);
        CodeParser parser             = new CodeParser(gitService);
        MetricsCalculator metricsCalc = new MetricsCalculator();
        ReleaseSelector selector      = new ReleaseSelector();
        JiraClient jira               = new JiraClient();
        CSVWriter csvWriter           = new CSVWriter();

        // 4) for each project
        for (ProjectConfig cfg : configs) {
            String owner = cfg.getOwner();
            String repo  = cfg.getName().toLowerCase();

            // --- TAGS ---
            logger.info("Project {}: fetching tags", cfg.getName());
            var tags = gitService.listTags(owner, repo);
            logger.debug("Found {} tags", tags.size());
            var selected = selector.selectFirstPercent(tags, cfg.getPercentage());
            logger.info("Selected {} tags ({}%)", selected.size(), cfg.getPercentage());

            // --- JIRA mapping (once per release) ---
            List<MethodData> allMethods = new ArrayList<>();
            jira.initialize(cfg.getJiraProjectKey());
            List<String> bugKeys = jira.fetchBugKeys();
            logger.info("JIRA returned {} bug issues", bugKeys.size());

            for (String tag : selected) {
                // 1) build map file→JIRA-keys
                logger.info("Building file→JIRA-keys map for {}/{}@{}", owner, repo, tag);
                Map<String,List<String>> fileToKeys =
                        gitService.getFileToIssueKeysMap(owner, repo, tag);

                // 2) parse & metrics
                logger.info("Analyzing {}@{}", repo, tag);
                var methods = parser.parseAndComputeOnline(owner, repo, tag, metricsCalc, fileToKeys);

                // 3) label buggy = intersection(commitHashes, bugKeys)
                methods = methods.stream()
                        .map(md -> md.toBuilder()
                                .buggy(jira.isMethodBuggy(md.getCommitHashes(), bugKeys))
                                .build())
                        .collect(Collectors.toList());

                long cnt = methods.stream().filter(MethodData::isBuggy).count();
                logger.info(" → Found {} buggy methods in {}", cnt, tag);

                allMethods.addAll(methods);
            }

            // --- CSV export ---
            Files.createDirectories(Paths.get("output"));
            String outCsv = "output/" + repo + "_dataset.csv";
            logger.info("Writing {} records to {}", allMethods.size(), outCsv);
            csvWriter.write(outCsv, allMethods);
            logger.info("CSV generated at {}", outCsv);
        }
    }
}