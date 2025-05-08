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
import com.mantimetrics.parser.CodeParserException;
import com.mantimetrics.release.ReleaseSelector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class MantiMetrics {
    private static final Logger log = LoggerFactory.getLogger(MantiMetrics.class);

    public static void main(String[] args) throws Exception {

        /* ---------- configs ---------- */
        ProjectConfig[] configs = ProjectConfigLoader.load();

        Properties p = new Properties();
        try (var in = MantiMetrics.class.getResourceAsStream("/github.properties")) {
            p.load(Objects.requireNonNull(in, "github.properties missing"));
        }
        GitService        git    = new GitService(p.getProperty("github.pat").trim());
        CodeParser        parser = new CodeParser(git);
        MetricsCalculator calc   = new MetricsCalculator();
        ReleaseSelector   sel    = new ReleaseSelector();
        JiraClient        jira   = new JiraClient();
        CSVWriter         csvOut = new CSVWriter();

        /* ---------- projects ---------- */
        for (ProjectConfig cfg : configs) {
            String owner = cfg.getOwner();
            String repo  = cfg.getName().toLowerCase();

            List<String> tags    = git.listTags(owner, repo);
            List<String> chosen  = sel.selectFirstPercent(tags, cfg.getPercentage());
            String branch        = git.getDefaultBranch(owner, repo);
            log.info("{}/{} – processing {} tags", repo, branch, chosen.size());

            jira.initialize(cfg.getJiraProjectKey());
            List<String> bugKeys = jira.fetchBugKeys();

            Map<String,List<String>> file2Keys;
            try {
                file2Keys = git.getFileToIssueKeysMap(owner, repo, branch);
            } catch (FileKeyMappingException ex) {
                log.error("Skipping {} – {}", repo, ex.getMessage());
                continue;
            }

            /* open CSV once, in appending */
            Path csvPath = Paths.get("output", repo + "_dataset.csv");
            try (BufferedWriter w = csvOut.open(csvPath)) {

                for (String tag : chosen) {
                    try {
                        List<MethodData> methods = parser
                                .parseAndComputeOnline(owner, repo, tag, calc, file2Keys)
                                .stream()
                                .map(m -> m.toBuilder()
                                        .buggy(jira.isMethodBuggy(m.getCommitHashes(), bugKeys))
                                        .build())
                                .collect(Collectors.toList());

                        csvOut.append(w, methods);

                        long buggy = methods.stream().filter(MethodData::isBuggy).count();
                        log.info("{}@{} → {} methods ({} buggy) [saved]", repo, tag,
                                methods.size(), buggy);

                    } catch (CodeParserException cpe) {          // <── NOVITÀ
                        log.error("❌ {}@{} skipped – {}", repo, tag, cpe.getMessage());
                    }
                }
            }
        }
        cleanup(git);
    }

    /* ---------- temp cleanup ---------- */
    private static void cleanup(GitService git) {
        for (Path d : git.getTmp()) try (Stream<Path> w = Files.walk(d)) {
            w.sorted(Comparator.reverseOrder()).forEach(p -> {
                try { Files.deleteIfExists(p); } catch (Exception ignore) {}
            });
        } catch (Exception ignore) {}
    }
}