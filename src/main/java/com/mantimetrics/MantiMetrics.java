package com.mantimetrics;

import com.mantimetrics.config.ProjectConfigLoader;
import com.mantimetrics.csv.CSVWriter;
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
        GitService git  = new GitService(pat);
        CodeParser parser = new CodeParser(git);
        MetricsCalculator calc = new MetricsCalculator();
        ReleaseSelector selector = new ReleaseSelector();
        JiraClient jira = new JiraClient();
        CSVWriter writer = new CSVWriter();

        /* ---------- projects loop ---------- */
        for (ProjectConfig cfg : configs) {
            String owner = cfg.getOwner();
            String repo  = cfg.getName().toLowerCase();

            /* tags */
            var tags = git.listTags(owner, repo);
            var chosen = selector.selectFirstPercent(tags, cfg.getPercentage());
            log.info("{} – selected {} / {} tags", repo, chosen.size(), tags.size());

            /* one-shot JIRA fetch and one-shot file→keys map */
            jira.initialize(cfg.getJiraProjectKey());
            var bugKeys = jira.fetchBugKeys();

            var fileToKeys = git.getFileToIssueKeysMap(
                    owner, repo, "refs/heads/" + (tags.isEmpty() ? "master" : "main"));

            /* analyse releases */
            List<MethodData> all = new ArrayList<>();
            for (String tag : chosen) {

                var methods = parser.parseAndComputeOnline(owner, repo, tag, calc, fileToKeys)
                        .stream()
                        .map(m -> m.toBuilder()
                                .buggy(jira.isMethodBuggy(m.getCommitHashes(), bugKeys))
                                .build())
                        .collect(Collectors.toList());

                log.info("{}@{} → {} methods ({} buggy)",
                        repo, tag, methods.size(),
                        methods.stream().filter(MethodData::isBuggy).count());

                all.addAll(methods);
            }

            /* CSV */
            Path out = Paths.get("output", repo + "_dataset.csv");
            Files.createDirectories(out.getParent());
            writer.write(out, all);
        }

        /* cleanup temp repos */
        cleanupTempDirs(git);
    }

    private static void cleanupTempDirs(GitService g) {
        for (Path d : g.getTempDirs()) try (Stream<Path> w = Files.walk(d)) {
            w.sorted(Comparator.reverseOrder()).forEach(p -> {
                try { Files.deleteIfExists(p); } catch (Exception ignore) {}
            });
            log.info("Deleted {}", d);
        } catch (Exception e) { log.warn("Cleanup failed: {}", e.getMessage()); }
    }
}