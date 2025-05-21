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
import com.mantimetrics.pmd.PmdAnalyzer;
import com.mantimetrics.release.ReleaseSelector;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class MantiMetrics {
    private static final Logger log = LoggerFactory.getLogger(MantiMetrics.class);
    private static CodeParser parser;
    private static MetricsCalculator calc;
    private static JiraClient jira;

    /**
     * Main method to run the MantiMetrics application.
     *
     * @param args command line arguments (not used)
     * @throws Exception if an error occurs during execution
     */
    public static void main(String[] args) throws Exception {
        ProjectConfig[] configs = ProjectConfigLoader.load();
        Properties p = new Properties();
        try (var in = MantiMetrics.class.getResourceAsStream("/github.properties")) {
            p.load(Objects.requireNonNull(in, "github.properties missing"));
        }

        GitService        git    = new GitService(p.getProperty("github.pat").trim());
        ReleaseSelector   sel    = new ReleaseSelector();
        CSVWriter         csvOut = new CSVWriter();
        PmdAnalyzer       pmd    = new PmdAnalyzer(Paths.get("rulesets/java/quickstart.xml"));
        setParser(new CodeParser(git));
        setCalc(new MetricsCalculator());
        setJira(new JiraClient());

        for (ProjectConfig cfg : configs) {
            String owner = cfg.getOwner();
            String repo  = cfg.getName().toLowerCase();

            // 1) list of all tags and selection of target releases
            List<String> tags    = git.listTags(owner, repo);
            List<String> chosen  = sel.selectFirstPercent(tags, cfg.getPercentage());
            chosen.sort((t1, t2) -> git.compareTagDates(owner, repo, t1, t2));

            String branch = git.getDefaultBranch(owner, repo);
            log.info("{}/{} – processing {} tags", repo, branch, chosen.size());

            // 2) init JIRA
            jira.initialize(cfg.getJiraProjectKey());
            List<String> bugKeys = jira.fetchBugKeys();
            log.info("Found {} bug keys", bugKeys.size());

            // 3) mapping file → issue keys
            Map<String,List<String>> file2Keys;
            try {
                file2Keys = git.getFileToIssueKeysMap(owner, repo, branch);
            } catch (FileKeyMappingException ex) {
                log.error("Skipping {} – {}", repo, ex.getMessage());
                continue;
            }
            log.info("File to issue keys mapping: {} issue keys", file2Keys.size());

            // CSV preparation
            Path csvPath = Paths.get("output", repo + "_dataset.csv");
            try (BufferedWriter w = csvOut.open(csvPath)) {
                log.info("Writing CSV to {}", csvPath);

                // map to hold data from the last release
                Map<String,MethodData> prevData = new HashMap<>();

                for (int i = 0; i < chosen.size(); i++) {
                    String tag     = chosen.get(i);
                    String prevTag = (i > 0 ? chosen.get(i - 1) : null);

                    log.info("Processing release {} (previous {})", tag, prevTag);

                    try {
                        // a) PMD on the code at the last commit of this release
                        int codeSmells = pmd.analyze(Paths.get("src")).getViolations().size();

                        // b) parsing + metrics, limiting commits between prevTag and tag
                        List<MethodData> methods = getCollect(owner, repo, tag, file2Keys, prevData, codeSmells, bugKeys);

                        // update prevDate
                        prevData = methods.stream()
                                .collect(Collectors.toMap(
                                        MethodData::getUniqueKey,
                                        m -> m
                                ));

                        // I write on the CSV
                        csvOut.append(w, methods);

                        long buggy = methods.stream().filter(MethodData::isBuggy).count();
                        log.info("{}@{} → {} methods ({} buggy) [saved]", repo, tag,
                                methods.size(), buggy);

                    } catch (CodeParserException cpe) {
                        log.error("❌ {}@{} skipped – {}", repo, tag, cpe.getMessage());
                    }
                }
            }
        }
        cleanup(git);
        log.info("Done! All temporary files cleaned up.");
    }

    @NotNull
    private static List<MethodData> getCollect(String owner, String repo, String tag, Map<String, List<String>> file2Keys, Map<String, MethodData> prevData, int codeSmells, List<String> bugKeys) throws CodeParserException {
        List<MethodData> methods = parser
                .parseAndComputeOnline(owner, repo, tag, calc, file2Keys);

        // Duplicate management: maintains the last value per key
        Map<String, MethodData> uniqueMethods = methods.stream()
                .collect(Collectors.toMap(
                        MethodData::getUniqueKey,
                        m -> m,
                        (existing, replacement) -> replacement
                ));

        return uniqueMethods.values().stream()
                // filter: only methods with at least one commit in the range
                .filter(m -> !m.getCommitHashes().isEmpty())
                .map(m -> {
                    int touches = Math.max(0, m.getCommitHashes().size() - 1);
                    MethodData prev = prevData.get(m.getUniqueKey());
                    int prevSmells = (prev != null ? prev.getCodeSmells() : 0);
                    boolean prevBuggy = (prev != null && prev.isBuggy());
                    return m.toBuilder()
                            .codeSmells(codeSmells)
                            .touches(touches)
                            .buggy(jira.isMethodBuggy(m.getCommitHashes(), bugKeys))
                            .prevCodeSmells(prevSmells)
                            .prevBuggy(prevBuggy)
                            .build();
                })
                .collect(Collectors.toList());
    }

    /* ---------- temp cleanup ---------- */
    private static void cleanup(GitService git) {
        for (Path d : git.getTmp()) try (Stream<Path> w = Files.walk(d)) {
            w.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (Exception e) {
                    log.warn("[INT] Cannot delete {}: {}", p, e.getMessage());
                }
            });
        } catch (Exception e) {
            log.warn("[EXT] Cannot delete {}: {}", d, e.getMessage());
        }
    }

    public static void setJira(JiraClient jira) {
        MantiMetrics.jira = jira;
    }

    public static void setCalc(MetricsCalculator calc) {
        MantiMetrics.calc = calc;
    }

    public static void setParser(CodeParser parser) {
        MantiMetrics.parser = parser;
    }
}