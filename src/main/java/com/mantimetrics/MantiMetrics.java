package com.mantimetrics;

import com.mantimetrics.config.ProjectConfigLoader;
import com.mantimetrics.csv.CSVWriter;
import com.mantimetrics.csv.CsvWriteException;
import com.mantimetrics.git.FileKeyMappingException;
import com.mantimetrics.git.GitService;
import com.mantimetrics.git.ProjectConfig;
import com.mantimetrics.jira.JiraClient;
import com.mantimetrics.jira.JiraClientException;
import com.mantimetrics.metrics.MetricsCalculator;
import com.mantimetrics.model.ClassData;
import com.mantimetrics.model.DatasetRow;
import com.mantimetrics.model.MethodData;
import com.mantimetrics.parser.CodeParser;
import com.mantimetrics.parser.CodeParserException;
import com.mantimetrics.pmd.PmdAnalyzer;
import com.mantimetrics.release.ReleaseProcessingException;
import com.mantimetrics.release.ReleaseSelector;
import net.sourceforge.pmd.reporting.Report;
import net.sourceforge.pmd.reporting.RuleViolation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.IOException;
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
        final Granularity granularity;
        try {
            granularity = parseGranularityOrFail(args);
        } catch (IllegalArgumentException e) {
            log.error("Errore: {}", e.getMessage());
            log.error("Uso: --granularity=method|class (oppure -g method|class)");
            return;
        }

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
            processProject(cfg, git, sel, csvOut, pmd, granularity);
        }

        cleanup(git);
        log.info("Done! All temporary files cleaned up.");
    }

    private static Granularity parseGranularityOrFail(String[] args) {
        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            if (a.startsWith("--granularity=")) {
                return Granularity.fromCli(a.substring("--granularity=".length()));
            }
            if (a.equals("--granularity") || a.equals("-g")) {
                if (i + 1 >= args.length) {
                    throw new IllegalArgumentException("Manca il valore dopo " + a + " (usa method|class)");
                }
                return Granularity.fromCli(args[i + 1]);
            }
        }
        throw new IllegalArgumentException("Flag obbligatorio mancante: --granularity=method|class (oppure -g method|class)");
    }

    /**
     * Processes a project configuration by fetching tags, selecting releases,
     * and analyzing code with PMD.
     *
     * @param cfg    the project configuration
     * @param git    the GitService instance
     * @param sel    the ReleaseSelector instance
     * @param csvOut the CSVWriter instance for output
     * @param pmd    the PmdAnalyzer instance for code analysis
     */
    private static void processProject(
            ProjectConfig cfg,
            GitService git,
            ReleaseSelector sel,
            CSVWriter csvOut,
            PmdAnalyzer pmd,
            Granularity granularity
    ) throws JiraClientException, CsvWriteException {

        String owner = cfg.owner();
        String repo  = cfg.name().toLowerCase();

        List<String> validTags = getTags(cfg, git, owner, repo);
        if (validTags == null) return;

        List<String> chosen = sel.selectFirstPercent(validTags, cfg.percentage());
        chosen.sort((t1, t2) -> git.compareTagDates(owner, repo, t1, t2));

        log.info("{} - percentage {}% → {} release to be processed",
                repo, cfg.percentage(), chosen.size());

        String branch = git.getDefaultBranch(owner, repo);
        log.info("{}/{} – processing {} tags", repo, branch, chosen.size());

        jira.initialize(cfg.jiraProjectKey());
        List<String> bugKeys = jira.fetchBugKeys();
        log.info("Found {} bug keys", bugKeys.size());

        Map<String, List<String>> file2Keys = getFile2Keys(git, owner, repo, branch);
        if (file2Keys == null) return;

        Path csvPath = Paths.get("output", repo + "_dataset_" + granularity.name().toLowerCase() + ".csv");
        try (BufferedWriter w = csvOut.open(csvPath, granularity)) {
            log.info("Writing CSV to {}", csvPath);

            Map<String, DatasetRow> prevData = new HashMap<>();

            ProjectContext ctx = new ProjectContext.Builder()
                    .owner(owner)
                    .repo(repo)
                    .granularity(granularity)
                    .git(git)
                    .pmd(pmd)
                    .csvOut(csvOut)
                    .file2Keys(file2Keys)
                    .prevData(prevData)
                    .bugKeys(bugKeys)
                    .writer(w)
                    .build();

            for (int i = 0; i < chosen.size(); i++) {
                String tag     = chosen.get(i);
                String prevTag = (i > 0 ? chosen.get(i - 1) : null);
                processRelease(tag, prevTag, ctx);
            }
        } catch (IOException e) {
            throw new ReleaseProcessingException("I/O error when writing CSV", e);
        }
    }

    /**
     * Processes a single release:
     * - downloads the source code for the current tag;
     * - performs PMD analysis and calculates code-smells;
     * - reconstructs the touch-map between the previous and the current release;
     * - enriches the MethodData with metrics, bugginess, and history;
     * - appends the result to the CSV;
     * - updates the shared state (prevData) for the next iteration.
     *
     * @param tag     current release tag
     * @param prevTag previous release tag (can be {@code null} if it does not exist)
     * @param ctx     project context encapsulating services, status, and output
     */
    private static void processRelease(
            @NotNull String tag,
            @Nullable String prevTag,
            @NotNull ProjectContext ctx) {

        log.info("▶️  {}@{} (prev={}) – start [{}]", ctx.repo, tag, prevTag, ctx.granularity);

        try {
            /* 1) code checkout ------------------------------------------ */
            Path releaseDir = parser.downloadRelease(ctx.owner, ctx.repo, tag);

            /* 2) PMD analysis --------------------------------------------------- */
            Report report = ctx.pmd.analyze(releaseDir);
            if (report.getViolations().isEmpty()) {
                log.warn("{}@{} - no PMD violation found: check ruleset and input path",
                        ctx.repo, tag);
            }

            List<RuleViolation> violations = report.getViolations();
            log.info("{}@{} - {} code-smell totals", ctx.repo, tag, violations.size());

            /* 3) touch-map between prevTag and tag ----------------------------------- */
            Map<String, List<String>> touchMap =
                    ctx.git.buildTouchMap(ctx.owner, ctx.repo, prevTag, tag);
            log.info("{}@{} - {} files touched", ctx.repo, tag, touchMap.size());

            /* 4) source parsing + feature construction ------------------------ */
            List<? extends DatasetRow> rows =
                    (ctx.granularity == Granularity.CLASS)
                            ? getCollectClasses(touchMap, releaseDir, ctx.owner + ";" + ctx.repo + ";" + tag,
                            ctx.file2Keys, ctx.prevData, violations, ctx.bugKeys)
                            : getCollectMethods(touchMap, releaseDir, ctx.owner + ";" + ctx.repo + ";" + tag,
                            ctx.file2Keys, ctx.prevData, violations, ctx.bugKeys);

            log.info("{}@{} - rowsFinali={} (violationsThisRelease={})",
                    ctx.repo, tag, rows.size(), violations.size());

            /* 5) update status prevDate for next release ---------- */
            ctx.prevData.clear();
            ctx.prevData.putAll(rows.stream()
                    .collect(Collectors.toMap(
                            DatasetRow::getUniqueKey,
                            r -> r,
                            (a, b) -> b
                    )));

            /* 6) writing to CSV ---------------------------------------------- */
            ctx.csvOut.append(ctx.writer, rows);

            long buggy = rows.stream().filter(DatasetRow::isBuggy).count();
            log.info("✔️  {}@{} - saved {} rows ({} buggy)", ctx.repo, tag, rows.size(), buggy);

        } catch (CodeParserException | CsvWriteException ex) {
            log.error("❌  {}@{} - release skipped: {}", ctx.repo, tag, ex.getMessage());
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new ReleaseProcessingException(
                    "Thread interrupted during release processing " + tag, ie);
        } catch (IOException ioe) {
            throw new ReleaseProcessingException(
                    "I/O error during release processing " + tag, ioe);
        }
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
    private static List<MethodData> getCollectMethods(
            Map<String, List<String>> touchMap,
            Path releaseDir,
            String info,
            Map<String, List<String>> file2Keys,
            Map<String, DatasetRow> prevData,
            List<RuleViolation> violations,
            List<String> bugKeys
    ) {
        String[] parts = info.split(";");
        String repo = parts[1];
        String tag  = parts[2];

        // 1) initial parsing: all MethodData have commitHashes = emptyList()
        List<MethodData> methods = parser.parseFromDirectory(releaseDir, repo, tag, calc, file2Keys);

        // 2) I delete duplicates according to uniqueKey (I keep the last one)
        Map<String, MethodData> unique = methods.stream()
                .collect(Collectors.toMap(
                        MethodData::getUniqueKey,
                        m -> m,
                        (prev, next) -> next
                ));

        // 3) grouping PMD violations by file name (e.g. 'MyClass.java')
        Map<String, List<RuleViolation>> byFileName = violations.stream()
                .collect(Collectors.groupingBy(v ->
                        Paths.get(v.getFileId().getFileName()).getFileName().toString()
                ));

        List<MethodData> result = new ArrayList<>();

        for (MethodData m : unique.values()) {
            // 4.1) retrieve the 'pure name' of the file (e.g. 'MyClass.java')
            String fileName = Paths.get(m.getPath()).getFileName().toString();

            // 4.2) normalise relUnixPath by removing initial/final slash
            String relUnixPath = m.getPath();
            if (relUnixPath.startsWith("/")) relUnixPath = relUnixPath.substring(1);
            if (relUnixPath.endsWith("/")) relUnixPath = relUnixPath.substring(0, relUnixPath.length() - 1);

            List<String> jiraKeysForFile = file2Keys.getOrDefault(relUnixPath, List.of());

            // 4.3) I request the list of commits
            List<String> actualCommits = touchMap.getOrDefault(relUnixPath, List.of());
            int touches = actualCommits.size();

            // 4.4) codeSmells account within the method
            List<RuleViolation> vlist = byFileName.getOrDefault(fileName, List.of());
            long cnt = vlist.stream()
                    .filter(v -> {
                        int r = v.getBeginLine();
                        return (r >= m.getStartLine() && r <= m.getEndLine());
                    })
                    .count();

            // 4.5) I get prevSmells and prevBuggy from the previous release
            DatasetRow prevRow = prevData.get(m.getUniqueKey());
            MethodData prev = (prevRow instanceof MethodData md) ? md : null;

            int prevSmells = (prev != null ? prev.getCodeSmells() : 0);
            boolean prevBuggy = (prev != null && prev.isBuggy());

            // 4.6) I rebuild MethodData with updated commitHashes and touches
            MethodData finalMethod = m.toBuilder()
                    .commitHashes(actualCommits)
                    .codeSmells((int) cnt)
                    .touches(touches)
                    .buggy(jira.isMethodBuggy(jiraKeysForFile, bugKeys))
                    .prevCodeSmells(prevSmells)
                    .prevBuggy(prevBuggy)
                    .build();

            // 4.7) I only add the methods that have actually been touched at least once
            if (touches > 0) result.add(finalMethod);
        }

        return result;
    }

    /**
     * Collects classes from the given release directory, filtering and enriching them with
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
    private static List<ClassData> getCollectClasses(
            Map<String, List<String>> touchMap,
            Path releaseDir,
            String info,
            Map<String, List<String>> file2Keys,
            Map<String, DatasetRow> prevData,
            List<RuleViolation> violations,
            List<String> bugKeys
    ) {
        String[] parts = info.split(";");
        String repo = parts[1];
        String tag  = parts[2];

        List<ClassData> classes = parser.parseClassesFromDirectory(releaseDir, repo, tag, calc, file2Keys);

        Map<String, ClassData> unique = classes.stream()
                .collect(Collectors.toMap(
                        ClassData::getUniqueKey,
                        c -> c,
                        (prev, next) -> next
                ));

        Map<String, List<RuleViolation>> byFileName = violations.stream()
                .collect(Collectors.groupingBy(v ->
                        Paths.get(v.getFileId().getFileName()).getFileName().toString()
                ));

        List<ClassData> result = new ArrayList<>();

        for (ClassData c : unique.values()) {
            String fileName = Paths.get(c.getPath()).getFileName().toString();

            String relUnixPath = c.getPath();
            if (relUnixPath.startsWith("/")) relUnixPath = relUnixPath.substring(1);
            if (relUnixPath.endsWith("/")) relUnixPath = relUnixPath.substring(0, relUnixPath.length() - 1);

            List<String> jiraKeysForFile = file2Keys.getOrDefault(relUnixPath, List.of());

            List<String> actualCommits = touchMap.getOrDefault(relUnixPath, List.of());
            int touches = actualCommits.size();

            List<RuleViolation> vlist = byFileName.getOrDefault(fileName, List.of());
            long cnt = vlist.stream()
                    .filter(v -> {
                        int r = v.getBeginLine();
                        return (r >= c.getStartLine() && r <= c.getEndLine());
                    })
                    .count();

            DatasetRow prevRow = prevData.get(c.getUniqueKey());
            ClassData prev = (prevRow instanceof ClassData cd) ? cd : null;

            int prevSmells = (prev != null ? prev.getCodeSmells() : 0);
            boolean prevBuggy = (prev != null && prev.isBuggy());

            ClassData finalClass = c.toBuilder()
                    .commitHashes(actualCommits)
                    .codeSmells((int) cnt)
                    .touches(touches)
                    .buggy(jira.isMethodBuggy(jiraKeysForFile, bugKeys)) // stessa logica: file->bug keys
                    .prevCodeSmells(prevSmells)
                    .prevBuggy(prevBuggy)
                    .build();

            if (touches > 0) result.add(finalClass);
        }

        return result;
    }

    @Nullable
    private static Map<String, List<String>> getFile2Keys(GitService git, String owner, String repo, String branch) {
        Map<String, List<String>> file2Keys;
        try {
            log.info("Building file → issue keys mapping");
            file2Keys = git.getFileToIssueKeysMap(owner, repo, branch);
        } catch (FileKeyMappingException ex) {
            log.error("Skipping {} – {}", repo, ex.getMessage());
            return null;
        }
        log.info("File to issue keys mapping: {} issue keys", file2Keys.size());
        return file2Keys;
    }

    @Nullable
    private static List<String> getTags(ProjectConfig cfg, GitService git, String owner, String repo) throws JiraClientException {
        List<String> gitTagsRaw    = git.listTags(owner, repo);
        List<String> gitTagsNorm  = gitTagsRaw.stream()
                .map(JiraClient::normalize)
                .toList();

        jira.initialize(cfg.jiraProjectKey());
        List<String> jiraVersions = jira.fetchProjectVersions(cfg.jiraProjectKey());

        // intersection
        Set<String> validNorm = new HashSet<>(gitTagsNorm);
        validNorm.retainAll(jiraVersions);

        log.info("{} - total Git tags: {}, Jira versions: {}, common: {}",
                repo, gitTagsRaw.size(), jiraVersions.size(), validNorm.size());

        if (validNorm.isEmpty()) {
            log.warn("No valid release in common → project skipped");
            return null;
        }

        // I reconstruct the original tags corresponding to the normalized list
        return gitTagsRaw.stream()
                .filter(t -> validNorm.contains(JiraClient.normalize(t)))
                .toList();
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

    public static void setJira(JiraClient jira) { MantiMetrics.jira = jira; }
    public static void setCalc(MetricsCalculator calc) { MantiMetrics.calc = calc; }
    public static void setParser(CodeParser parser) { MantiMetrics.parser = parser; }
}