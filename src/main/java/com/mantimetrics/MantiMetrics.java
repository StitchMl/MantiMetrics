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
import net.sourceforge.pmd.reporting.Report;
import net.sourceforge.pmd.reporting.RuleViolation;
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
        PmdAnalyzer       pmd    = new PmdAnalyzer();
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
                log.info("Building file → issue keys mapping");
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
                        // a.0) download the current release into a temporary folder
                        Path releaseDir = parser.downloadRelease(owner, repo, tag);

                        // a.1) I parse all .java under releaseDir/src
                        Report report = pmd.analyze(releaseDir);
                        if (report.getViolations().isEmpty())
                            log.warn("No violations found: check ruleset and input paths");

                        List<RuleViolation> violations = report.getViolations();
                        int codeSmells = violations.size();
                        log.info("{}@{} – {} code smells", repo, tag, codeSmells);

                        Map<String,List<String>> touchMap =
                                git.buildTouchMap(owner, repo, prevTag, tag);
                        log.info("{}@{} – {} files touched", repo, tag, touchMap.size());

                        // b) parsing + metrics on the same folder
                        List<MethodData> methods = getCollect(
                                touchMap, releaseDir, owner + ";" + repo + ";" + tag,
                                file2Keys, prevData,
                                violations, bugKeys
                        );

                        log.info("release={} violationsTotali={} methodsFinali={}",
                                tag, violations.size(), methods.size());

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

    /**
     * Collects methods from the given release directory, filtering and enriching them with
     * code smells, touches, previous data, and bug keys.
     *
     * @param releaseDir  the path to the release directory
     * @param info       a string containing owner, repo, and tag information
     * @param prevData    a map of previous method data
     * @param violations  a list of rule violations
     * @param bugKeys     a list of bug keys
     * @return a list of enriched method data
     */
    @NotNull
    private static List<MethodData> getCollect(
            Map<String,List<String>> touchMap,
            Path releaseDir,
            String info,
            Map<String, List<String>> file2Keys,
            Map<String, MethodData> prevData,
            List<RuleViolation> violations,
            List<String> bugKeys
    ){

        String[] parts = info.split(";");
        String repo  = parts[1];
        String tag   = parts[2];

        // 1) initial parsing: all MethodData have commitHashes = emptyList()
        List<MethodData> methods = parser.parseFromDirectory(
                releaseDir, repo, tag, calc, file2Keys
        );

        // 2) I delete duplicates according to uniqueKey (I keep the last one)
        Map<String, MethodData> uniqueMethods = methods.stream()
                .collect(Collectors.toMap(
                        MethodData::getUniqueKey,
                        m -> m,
                        (prev, next) -> next
                ));

        // 3) grouping PMD violations by file name (e.g. 'MyClass.java')
        Map<String, List<RuleViolation>> byFileName = violations.stream()
                .collect(Collectors.groupingBy(v ->
                        Paths.get(v.getFileId().getFileName())
                                .getFileName().toString()
                ));

        List<MethodData> result = new ArrayList<>();

        for (MethodData m : uniqueMethods.values()) {
            // 4.1) retrieve the 'pure name' of the file (e.g. 'MyClass.java')
            String methodFileName = Paths.get(m.getPath())
                    .getFileName()
                    .toString();

            // 4.2) normalise relUnixPath by removing initial/final slash
            String relUnixPath = m.getPath();
            if (relUnixPath.startsWith("/")) {
                relUnixPath = relUnixPath.substring(1);
            }
            if (relUnixPath.endsWith("/")) {
                relUnixPath = relUnixPath.substring(0, relUnixPath.length() - 1);
            }
            List<String> jiraKeysForFile = file2Keys.getOrDefault(relUnixPath, List.of());

            // 4.3) I determine prevTag (null if first release)
            String prevTag = null;
            if (prevData.containsKey(m.getUniqueKey())) {
                prevTag = prevData.get(m.getUniqueKey()).getReleaseId();
            }

            log.debug("Requesting commits for filePath = [{}], fromTag = [{}]",
                    relUnixPath, prevTag);

            // 4.4) I request the list of commits
            List<String> actualCommits = touchMap.getOrDefault(relUnixPath, List.of());
            int touches = actualCommits.size();


            // 4.6) codeSmells account within the method
            List<RuleViolation> vlist = byFileName.getOrDefault(methodFileName, List.of());
            long cnt = vlist.stream()
                    .filter(v -> {
                        int r = v.getBeginLine();
                        return (r >= m.getStartLine() && r <= m.getEndLine());
                    })
                    .count();

            // 4.7) I get prevSmells and prevBuggy from the previous release
            MethodData prev = prevData.get(m.getUniqueKey());
            int prevSmells = (prev != null ? prev.getCodeSmells() : 0);
            boolean prevBuggy = (prev != null && prev.isBuggy());

            // 4.8) I rebuild MethodData with updated commitHashes and touches
            MethodData finalMethod = m.toBuilder()
                    .commitHashes(actualCommits)
                    .codeSmells((int) cnt)
                    .touches(touches)
                    .buggy(jira.isMethodBuggy(jiraKeysForFile,
                            bugKeys))
                    .prevCodeSmells(prevSmells)
                    .prevBuggy(prevBuggy)
                    .build();

            // 4.9) I only add the methods that have actually been touched at least once
            if (touches > 0) {
                result.add(finalMethod);
            }
        }

        return result;
    }

    /**
     * Cleans up temporary files created during the execution of the program.
     *
     * @param git the GitService instance used to manage temporary files
     */
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