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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

public class MantiMetrics {
    private static final Logger logger = LoggerFactory.getLogger(MantiMetrics.class);

    public static void main(String[] args) throws Exception {
        // 1) Loading configurations
        logger.info("Loading project configurations");
        ProjectConfig[] configs = ProjectConfigLoader.load();

        // 2) GitHub PAT
        Properties ghProps = new Properties();
        try (InputStream in = MantiMetrics.class.getResourceAsStream("/github.properties")) {
            if (in == null) {
                logger.error("Cannot find github.properties in resources");
                throw new IllegalStateException("Cannot find github.properties");
            }
            ghProps.load(in);
        }
        String githubPat = ghProps.getProperty("github.pat");
        if (githubPat == null || githubPat.isBlank()) {
            logger.error("github.pat is missing in github.properties");
            throw new IllegalStateException("The github.properties file must contain github.pat");
        }
        logger.info("GitHub PAT loaded");

        // 3) initialise services
        GitService gitService         = new GitService(githubPat);
        CodeParser parser             = new CodeParser(gitService);
        MetricsCalculator metricsCalc = new MetricsCalculator();
        JiraClient jira               = new JiraClient();
        ReleaseSelector selector      = new ReleaseSelector();
        CSVWriter csvWriter           = new CSVWriter();

        // 4) project cycle
        for (ProjectConfig cfg : configs) {
            String owner = cfg.getOwner();
            String repo  = cfg.getName().toLowerCase();

            // --- TAGS phase ---
            logger.info("Project {}: fetching tags", cfg.getName());
            List<String> tags = gitService.listTags(owner, repo);
            logger.debug("Found {} tags for project {}", tags.size(), cfg.getName());

            int pct = cfg.getPercentage();
            List<String> selected = selector.selectFirstPercent(tags, pct);
            logger.info("Project {}: selected {}% → {} tags", cfg.getName(), pct, selected.size());

            // --- PARSING & METRICS phase ---
            logger.info("Analyzing {} tags for project {}", selected.size(), cfg.getName());
            List<MethodData> allMethods = new ArrayList<>();
            for (String tag : selected) {
                logger.debug("Analyzing tag {}", tag);
                allMethods.addAll(parser.parseAndComputeOnline(owner, repo, tag, metricsCalc));
            }
            logger.info("Collected {} method data entries for {}", allMethods.size(), cfg.getName());

            // --- JIRA LABELING phase ---
            logger.info("Labeling buggy methods via JIRA for project {}", cfg.getName());
            jira.initialize(cfg.getJiraProjectKey());
            List<String> bugKeys = jira.fetchBugKeys();
            allMethods = allMethods.stream()
                    .map(md -> md.toBuilder()
                            .buggy(jira.isMethodBuggy(md.getCommitHashes(), bugKeys))
                            .build())
                    .collect(Collectors.toList());

            long buggyCount = allMethods.stream().filter(MethodData::isBuggy).count();
            logger.info("Labeled {} methods as buggy out of {}", buggyCount, allMethods.size());

            // --- EXPORT CSV phase ---
            Files.createDirectories(Paths.get("output"));
            String outCsv = "output/" + repo + "_dataset.csv";
            logger.info("Writing {} records to {}", allMethods.size(), outCsv);
            csvWriter.write(outCsv, allMethods);
            logger.info("CSV generated at {}", outCsv);
        }
    }
}