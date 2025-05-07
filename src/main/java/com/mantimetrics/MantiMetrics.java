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

import java.io.*;
import java.nio.file.*;
import java.util.stream.Stream;

public class MantiMetrics {
    private static final Logger logger = LoggerFactory.getLogger(MantiMetrics.class);

    public static void main(String[] args) throws Exception {
        logger.info("Loading project configurations");
        ProjectConfig[] configs = ProjectConfigLoader.load();

        // GitHub PAT
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

        // init services
        GitService gitService         = new GitService(githubPat);
        CodeParser parser             = new CodeParser(gitService);
        MetricsCalculator metricsCalc = new MetricsCalculator();
        ReleaseSelector selector      = new ReleaseSelector();
        JiraClient jira               = new JiraClient();
        CSVWriter csvWriter           = new CSVWriter();

        for (ProjectConfig cfg : configs) {
            String owner = cfg.getOwner();
            String repo  = cfg.getName().toLowerCase();

            // --- TAGS ---
            logger.info("Project {}: fetching tags", cfg.getName());
            var tags     = gitService.listTags(owner, repo);
            var selected = selector.selectFirstPercent(tags, cfg.getPercentage());
            logger.info("Selected {} tags ({}%)", selected.size(), cfg.getPercentage());

            // --- JIRA bug keys (one call only) ---
            jira.initialize(cfg.getJiraProjectKey());
            List<String> bugKeys = jira.fetchBugKeys();
            logger.info("JIRA returned {} bug issues", bugKeys.size());

            List<MethodData> allMethods = new ArrayList<>();
            for (String tag : selected) {
                logger.info("Building file→JIRA‑keys map for {}/{}@{}", owner, repo, tag);
                Map<String,List<String>> fileToKeys =
                        gitService.getFileToIssueKeysMap(owner, repo, tag);

                logger.info("Analyzing {}/{}@{}", owner, repo, tag);
                var methods = parser.parseAndComputeOnline(
                        owner, repo, tag, metricsCalc, fileToKeys);

                // buggy label
                methods = methods.stream()
                        .map(md -> md.toBuilder()
                                .buggy(jira.isMethodBuggy(md.getCommitHashes(), bugKeys))
                                .build())
                        .collect(Collectors.toList());

                long cnt = methods.stream().filter(MethodData::isBuggy).count();
                logger.info("Found {} buggy methods in {}", cnt, tag);
                allMethods.addAll(methods);
            }

            // export CSV
            Files.createDirectories(Paths.get("output"));
            String outCsv = "output/" + repo + "_dataset.csv";
            logger.info("Writing {} records to {}", allMethods.size(), outCsv);
            csvWriter.write(outCsv, allMethods);
            logger.info("CSV generated at {}", outCsv);

            // after exporting all CSVs:
            cleanupTempDirs(gitService);

            logger.info("Done!");
        }
    }

    private static void cleanupTempDirs(GitService gitService) {
        List<Path> dirs = gitService.getTempDirs();
        for (Path dir : dirs) {
            try (Stream<Path> walk = Files.walk(dir)) {
                walk.sorted(Comparator.reverseOrder())
                        .forEach(p -> {
                            try {
                                Files.deleteIfExists(p);
                            } catch (IOException e) {
                                // only DEBUG so as not to pollute WARN on Windows
                                logger.warn("Failed to delete {}: {}", p, e.getMessage());
                            }
                        });
                logger.info("Deleted temp repo {}", dir);
            } catch (IOException e) {
                logger.error("Error walking temp dir {}: {}", dir, e.getMessage());
            }
        }
    }
}