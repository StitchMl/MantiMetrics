package com.mantimetrics;

import com.mantimetrics.config.ProjectConfigLoader;
import com.mantimetrics.csv.CSVWriter;
import com.mantimetrics.git.FileKeyMappingException;
import com.mantimetrics.git.GitService;
import com.mantimetrics.git.ProjectConfig;
import com.mantimetrics.jira.JiraClient;
import com.mantimetrics.metrics.MetricsCalculator;
import com.mantimetrics.model.MethodData;
import com.mantimetrics.parser.CodeParser;
import com.mantimetrics.release.ReleaseSelector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class MantiMetrics {
    private static final Logger log = LoggerFactory.getLogger(MantiMetrics.class);

    public static void main(String[] args) throws Exception {

        /* ---------- loading configs ---------- */
        ProjectConfig[] configs = ProjectConfigLoader.load();

        Properties props = new Properties();
        try (var in = MantiMetrics.class.getResourceAsStream("/github.properties")) {
            props.load(Objects.requireNonNull(in, "github.properties missing"));
        }
        String pat = props.getProperty("github.pat").trim();

        /* ---------- services ---------- */
        GitService git   = new GitService(pat);
        CodeParser parser = new CodeParser(git);
        MetricsCalculator calc = new MetricsCalculator();
        ReleaseSelector selector = new ReleaseSelector();
        JiraClient jira = new JiraClient();
        CSVWriter csv   = new CSVWriter();

        for (ProjectConfig cfg : configs) {
            String owner = cfg.getOwner();
            String repo = cfg.getName().toLowerCase();

            List<String> tags = git.listTags(owner, repo);
            List<String> chosen = selector.selectFirstPercent(tags, cfg.getPercentage());
            String defBranch = git.getDefaultBranch(owner, repo);
            log.info("{}/{} – selected {} / {} tags", repo, defBranch, chosen.size(), tags.size());

            jira.initialize(cfg.getJiraProjectKey());
            List<String> bugKeys = jira.fetchBugKeys();

            /* one single map built on the tip of default_branch */
            String defaultBranch = git.getDefaultBranch(owner, repo);
            Map<String, List<String>> fileToKeys;
            try {
                fileToKeys = git.getFileToIssueKeysMap(owner, repo, defaultBranch);
            } catch (FileKeyMappingException e) {
                log.error("Skipping {} – {}", repo, e.getMessage());
                continue;
            }

            List<MethodData> all = new ArrayList<>();
            for (String tag : chosen) {
                List<MethodData> ms = parser
                        .parseAndComputeOnline(owner, repo, tag, calc, fileToKeys)
                        .stream()
                        .map(m -> m.toBuilder()
                                .buggy(jira.isMethodBuggy(m.getCommitHashes(), bugKeys))
                                .build())
                        .collect(Collectors.toList());
                log.info("{}@{} → {} methods ({} buggy)",
                        repo, tag, ms.size(),
                        ms.stream().filter(MethodData::isBuggy).count());
                all.addAll(ms);
            }

            Path out = Paths.get("output", repo + "_dataset.csv");
            Files.createDirectories(out.getParent());
            csv.write(out, all);
        }

        cleanupTempDirs(git);
    }

    private static void cleanupTempDirs(GitService g) {
        for (Path d : g.getTmpDirs()) try (Stream<Path> w = Files.walk(d)) {
            w.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (Exception e) {
                    log.warn("Failed to delete {}: {}", p, e.getMessage());
                }
            });
            log.info("Deleted {}", d);
        } catch (Exception e) { log.warn("Cleanup failed: {}", e.getMessage()); }
    }
}