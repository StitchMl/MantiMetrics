package com.mantimetrics.orchestrator;

import com.mantimetrics.releaseSelection.ReleaseTimelineJiraGit;

import com.mantimetrics.datasetOutput.CSVWriter;
import com.mantimetrics.datasetOutput.CSVException;
import com.mantimetrics.datasetOutput.DatasetArtifactGenerator;
import com.mantimetrics.git.GitFacade;
import com.mantimetrics.git.GitConfig;
import com.mantimetrics.history.StoreReleaseInMemory;
import com.mantimetrics.jira.JiraClientException;
import com.mantimetrics.labeling.ReleaseLabeling;
import com.mantimetrics.labeling.HistoricalBugTaker;
import com.mantimetrics.labeling.Proportion;
import com.mantimetrics.jira.JiraSnapshot;
import com.mantimetrics.git.RawReleaseCommits;
import com.mantimetrics.git.GitReleaseSnapshot;
import com.mantimetrics.datasetSetting.DatasetClassData;
import com.mantimetrics.datasetSetting.DatasetRow;
import com.mantimetrics.releaseSelection.ReleaseSnoringFilter;
import com.mantimetrics.releaseSelection.ReleaseTimeline;
import com.mantimetrics.javaParsing.JavaParsingException;
import com.mantimetrics.datasetOutput.MilestoneAuditWriter;
import com.mantimetrics.releaseSelection.ReleaseException;
import com.mantimetrics.smell.SonarClient;
import com.mantimetrics.smell.SonarException;
import com.mantimetrics.smell.SonarPreScanOrchestrator;
import com.mantimetrics.smell.SonarSmellIndex;
import com.mantimetrics.utility.ProgressBar;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Project-level orchestrator. It preloads the full release history once, builds the historical bug oracle,
 * then writes one raw dataset per requested granularity.
 */
public final class Orchestrator {
    private static final org.slf4j.Logger LOG =
            org.slf4j.LoggerFactory.getLogger(Orchestrator.class);

    private final ReleaseTimelineJiraGit releasePlanner;
    private final SingleReleaseExecution releaseExecutionService;
    private final GitFacade gitService;
    private final CSVWriter csvWriter;
    private final DatasetArtifactGenerator datasetArtifactService;
    private final MilestoneAuditWriter milestoneAuditService;
    private final SonarClient sonarCloudClient;
    private final SonarPreScanOrchestrator sonarPreScanService;

    /**
     * Creates the project processor with all collaborators needed to execute the full release pipeline.
     *
     * @param releasePlanner          planner that resolves the common release timeline
     * @param releaseExecutionService service that executes one prepared release
     * @param gitService              Git service used to preload release commit data
     * @param csvWriter               CSV writer used to open per-granularity output files
     * @param sonarPreScanService     service that runs {@code mvn sonar:sonar} for missing releases
     * @param sonarCloudClient        authenticated SonarCloud client shared across the pipeline
     * @param outputServices          groups {@link DatasetArtifactGenerator} and {@link MilestoneAuditWriter}
     */
    public Orchestrator(
            ReleaseTimelineJiraGit releasePlanner,
            SingleReleaseExecution releaseExecutionService,
            GitFacade gitService,
            CSVWriter csvWriter,
            SonarPreScanOrchestrator sonarPreScanService,
            SonarClient sonarCloudClient,
            OutputServices outputServices
    ) {
        this.releasePlanner = releasePlanner;
        this.releaseExecutionService = releaseExecutionService;
        this.gitService = gitService;
        this.csvWriter = csvWriter;
        this.sonarPreScanService = sonarPreScanService;
        this.sonarCloudClient = sonarCloudClient;
        this.datasetArtifactService = outputServices.datasetArtifactService();
        this.milestoneAuditService = outputServices.milestoneAuditService();
    }

    /**
     * Executes the end-to-end analysis for one project and all requested granularities.
     *
     * @param config project configuration to analyze
     * @param useGithubIssues whether to union GitHub Issues with Jira bug tickets
     * @param proportionVariant Proportion variant used to estimate the injected version
     * @param excludeChurnZero whether to drop rows whose current-release churn is zero
     * @throws JiraClientException when Jira metadata cannot be loaded
     * @throws CSVException when a dataset CSV file cannot be written or closed
     */
    public void process(GitConfig config, boolean useGithubIssues, Proportion.Variant proportionVariant,
            boolean excludeChurnZero)
            throws JiraClientException, CSVException {
        ReleasePlan plan = releasePlanner.plan(config, useGithubIssues);
        if (plan == null) {
            return;
        }

        LOG.info("+---------------------------------------------------------------------");
        LOG.info("|  Project  : {}", config.name());
        LOG.info("|  Releases : {} total  ->  {} selected ({}%)  |  {} bug tickets",
                plan.timeline().size(), plan.selectedTags().size(),
                config.percentage(), plan.resolvedTickets().size());
        LOG.info("+---------------------------------------------------------------------");

        // -- Phase 1: Git commit history --------------------------------------
        LOG.info("[1/5] Preloading Git commit history ({} releases)...", plan.timeline().size());
        List<ReleaseSnapshot> releaseHistory;
        try (ProgressBar bar = new ProgressBar("Git history", plan.timeline().size())) {
            releaseHistory = buildReleaseHistory(plan, bar, useGithubIssues);
        }
        LOG.info("[1/5] done - {} snapshots loaded", releaseHistory.size());

        // -- Phase 2: Bug-label oracle (sub-bars managed inside the builder) --
        LOG.info("[2/5] Building bug-label oracle ({} tickets, Proportion-{})...",
                plan.resolvedTickets().size(), proportionVariant);
        ReleaseLabeling labelIndex = new HistoricalBugTaker()
                .build(plan.timeline(), plan.selectedTags(), plan.resolvedTickets(), releaseHistory, proportionVariant);
        LOG.info("[2/5] done - linked={}, IV-JIRA={}, Proportion-fallback={}",
                labelIndex.summary().ticketsWithFixCommit(),
                labelIndex.summary().ticketsUsingAffectedVersions(),
                labelIndex.summary().ticketsUsingTotalFallback());

        // -- Phase 3a: SonarCloud per-release pre-scan -------------------------
        if (config.sonarProjectKey() != null) {
            int total = plan.timeline().size();
            LOG.info("[3a/5] SonarCloud pre-scan - {} releases (skips already-scanned)...", total);
            try (ProgressBar bar = new ProgressBar("Sonar pre-scan", total)) {
                int newScans = sonarPreScanService.scanMissingReleases(
                        plan.owner(), plan.repo(),
                        plan.timeline().orderedTags(),
                        config.sonarProjectKey(), bar);
                LOG.info("[3a/5] done - {} new releases scanned", newScans);
            } catch (SonarException e) {
                LOG.warn("[3a/5] SonarCloud pre-scan skipped: {}", e.getMessage());
            }
        }

        // -- Phase 3b: Build SonarCloud smell index ----------------------------
        String sonarLabel = config.sonarProjectKey() != null
                ? config.sonarProjectKey() : "n/a";
        LOG.info("[3b/5] Building SonarCloud smell index - {}...", sonarLabel);
        Map<String, Map<String, Integer>> sonarSmellsByTag;
        try (ProgressBar bar = new ProgressBar("Sonar index", plan.timeline().size())) {
            sonarSmellsByTag = buildSonarSmellsByTag(plan, config, bar);
        }
        LOG.info("[3b/5] done");

        // -- Phase 4: Dataset generation ---------------------------------------
        int releasesTotal = plan.selectedTags().size();
        LOG.info("[5/5] Generating dataset - {} releases...", releasesTotal);
        List<Path> csvPaths = new ArrayList<>();
        Map<String, JiraSnapshot> ticketsByKey = indexTicketsByKey(plan.allTickets());
        Map<String, Integer> openTicketsByRelease = computeOpenTicketsByRelease(plan);
        Map<String, Set<String>> ticketTouchedPaths = computeTicketTouchedPaths(releaseHistory);
        Map<String, List<String>> orderedTicketsByRelease = computeOrderedTicketsByRelease(plan);
        List<SharedStatus> contexts = openContexts(plan, csvPaths, labelIndex,
                sonarSmellsByTag, excludeChurnZero, ticketsByKey, openTicketsByRelease,
                ticketTouchedPaths, orderedTicketsByRelease);
        try (ProgressBar bar = new ProgressBar("Dataset", releasesTotal)) {
            int releasesDone = 0;
            try {
                for (ReleaseSnapshot snapshot : releaseHistory) {
                    if (!plan.selectedTags().contains(snapshot.tag())) {
                        continue;
                    }
                    releasesDone++;
                    bar.step(snapshot.tag());
                    LOG.info("[5/5] Release [{}/{}] {}", releasesDone, releasesTotal, snapshot.tag());
                    releaseExecutionService.processRelease(snapshot, contexts);
                }
            } finally {
                closeContexts(contexts);
            }
        }

        generateArtifacts(csvPaths, plan, labelIndex, releaseHistory);
        LOG.info("[OK] Dataset complete - output files written to output/");
    }

    /**
     * Builds the per-tag SonarCloud file-smell maps.
     *
     * <p>For each tag the lookup strategy is:
     * <ol>
     *   <li>Exact version match ({@code sonar.projectVersion == tag}) - populated by the pre-scan phase.</li>
     *   <li>Date-based fallback - latest analysis whose date <= release date, or earliest available proxy.</li>
     * </ol>
     * Returns an empty map gracefully when SonarCloud is not configured or the API is unavailable.
     *
     * @param plan   release plan providing the tag timeline
     * @param config project configuration carrying the optional SonarCloud key
     * @param bar    progress bar stepped once per tag
     * @return map of release tag -> (file-path -> smell-count)
     */
    private Map<String, Map<String, Integer>> buildSonarSmellsByTag(
            ReleasePlan plan, GitConfig config, ProgressBar bar) {
        SonarSmellIndex sonarIndex = SonarSmellIndex.EMPTY;
        if (config.sonarProjectKey() != null && !config.sonarProjectKey().isBlank()) {
            try {
                sonarIndex = SonarSmellIndex.build(sonarCloudClient, config.sonarProjectKey());
            } catch (SonarException e) {
                LOG.warn("SonarCloud unavailable for {}: {}", config.sonarProjectKey(), e.getMessage());
            }
        }
        Map<String, Map<String, Integer>> byTag = new LinkedHashMap<>();
        for (String tag : plan.timeline().orderedTags()) {
            // 1) Try exact version match (requires pre-scan to have run)
            Map<String, Integer> smells = sonarIndex.getSmellsForTag(tag);
            // 2) Fall back to nearest-by-date analysis
            if (smells.isEmpty()) {
                java.time.Instant tagDate = plan.timeline().tagDates().get(tag);
                smells = tagDate != null ? sonarIndex.getSmellsForDate(tagDate) : Map.of();
            }
            byTag.put(tag, smells);
            bar.step(tag);
        }
        return byTag;
    }

    /**
     * Opens one CSV writer and one independent history state per granularity so class-level and method-level
     * analyses can coexist without sharing mutable state.
     */
    private List<SharedStatus> openContexts(
            ReleasePlan plan,
            List<Path> csvPaths,
            ReleaseLabeling labelIndex,
            Map<String, Map<String, Integer>> sonarSmellsByTag,
            boolean excludeChurnZero,
            Map<String, JiraSnapshot> ticketsByKey,
            Map<String, Integer> openTicketsByRelease,
            Map<String, Set<String>> ticketTouchedPaths,
            Map<String, List<String>> orderedTicketsByRelease
    ) throws CSVException {
        List<SharedStatus> contexts = new ArrayList<>();
        try {
            Path csvPath = Paths.get("output", plan.repo() + "_dataset_class.csv");
            BufferedWriter writer = csvWriter.open(csvPath);
            csvPaths.add(csvPath);
            contexts.add(new SharedStatus(
                    plan.owner(),
                    plan.repo(),
                    csvWriter,
                    new HashMap<>(),
                    new StoreReleaseInMemory(),
                    labelIndex,
                    writer,
                    sonarSmellsByTag,
                    excludeChurnZero,
                    ticketsByKey,
                    openTicketsByRelease,
                    ticketTouchedPaths,
                    orderedTicketsByRelease
            ));
            return contexts;
        } catch (CSVException exception) {
            try {
                closeContexts(contexts);
            } catch (CSVException closeFailure) {
                exception.addSuppressed(closeFailure);
            }
            throw exception;
        }
    }

    /**
     * Preloads the complete release history, including the releases excluded by snoring, because those future
     * fix commits are still needed to label the older dataset rows.
     */
    private List<ReleaseSnapshot> buildReleaseHistory(ReleasePlan plan, ProgressBar bar, boolean includeGithub) {
        List<ReleaseSnapshot> history = new ArrayList<>();
        List<String> timelineTags = plan.timeline().orderedTags();
        int total = timelineTags.size();
        for (int index = 0; index < total; index++) {
            String tag = timelineTags.get(index);
            String previousTag = index > 0 ? timelineTags.get(index - 1) : null;
            try {
                history.add(new ReleaseSnapshot(
                        tag,
                        previousTag,
                        gitService.buildReleaseCommitData(plan.owner(), plan.repo(), previousTag, tag, includeGithub)
                ));
                bar.step(tag);
            } catch (IOException exception) {
                throw new ReleaseException("I/O error while preloading commit history for " + tag, exception);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new ReleaseException("Interrupted while preloading commit history for " + tag, exception);
            }
        }
        return history;
    }

    /**
     * Generates the derived artifacts and audit file for every produced raw dataset.
     *
     * @param csvPaths raw dataset paths produced by the pipeline
     * @param plan release plan associated with the project
     * @param labelIndex historical bug-label index used during labeling
     */
    private void generateArtifacts(
            List<Path> csvPaths,
            ReleasePlan plan,
            ReleaseLabeling labelIndex,
            List<ReleaseSnapshot> releaseHistory
    ) {
        double linkageRate = computeLinkageRate(releaseHistory);
        for (Path csvPath : csvPaths) {
            try {
                datasetArtifactService.generate(csvPath);
                milestoneAuditService.write(
                        csvPath,
                        plan.timeline().size(),
                        plan.selectedTags().size(),
                        labelIndex.summary(),
                        linkageRate
                );
            } catch (IOException exception) {
                throw new ReleaseException("I/O error when generating derived dataset artifacts", exception);
            }
        }
    }

    /**
     * Computes the project-level linkage rate as the proportion of unique commits (touching at least one
     * Java file) that carry a Jira issue key, aggregated across the full release history.
     *
     * @param releaseHistory complete list of preloaded release snapshots
     * @return linkage rate in [0.0, 1.0], or 0.0 when no Java commits are found
     */
    private double computeLinkageRate(List<ReleaseSnapshot> releaseHistory) {
        long totalJava = releaseHistory.stream()
                .mapToLong(s -> s.commitData().totalJavaCommits())
                .sum();
        long linkedJava = releaseHistory.stream()
                .mapToLong(s -> s.commitData().issueLinkedJavaCommits())
                .sum();
        return totalJava == 0 ? 0.0 : (double) linkedJava / totalJava;
    }

    // ==== Collect-once / derive-many: generate every dataset variant in one run ====

    private static final int[] SNORING_PERCENTAGES = {34, 20}; // keep first 34% (snoring 66%) / 20% (snoring 80%)
    private final ReleaseSnoringFilter variantSelector = new ReleaseSnoringFilter();

    /**
     * Runs the full analysis once and generates every dataset variant. The heavy work (raw Git
     * commit fetch, source download+parse, SonarCloud index, Jira/GitHub tickets) is done a single
     * time; each combination of the 4 experimental flags is then derived in memory (per-variant
     * commit aggregation, labeling, TLP, snoring and churn-zero filters).
     *
     * @param config project configuration
     * @throws JiraClientException when Jira metadata cannot be loaded
     * @throws CSVException when a variant CSV cannot be written
     */
    public void run(GitConfig config) throws JiraClientException, CSVException {
        ReleasePlan plan = releasePlanner.plan(config, false);
        if (plan == null) {
            return;
        }
        String owner = plan.owner();
        String repo = plan.repo();
        List<String> allTags = plan.timeline().orderedTags();

        LOG.info("+---------------------------------------------------------------------");
        LOG.info("|  Project  : {}  ({} releases | {} bug / {} all / {} GH tickets)",
                repo, plan.timeline().size(), plan.resolvedTickets().size(),
                plan.allTickets().size(), plan.ghTickets().size());
        LOG.info("+---------------------------------------------------------------------");

        // ---- COLLECT ONCE ----
        LOG.info("[collect 1/3] Fetching raw commit history ({} releases)...", allTags.size());
        Map<String, RawReleaseCommits> rawCommitsByTag;
        try (ProgressBar bar = new ProgressBar("Commits", allTags.size())) {
            rawCommitsByTag = collectRawCommits(owner, repo, allTags, bar);
        }

        int maxPct = 0;
        for (int p : SNORING_PERCENTAGES) {
            maxPct = Math.max(maxPct, p);
        }
        List<String> parseTags = variantSelector.selectFirstPercent(allTags, maxPct);

        if (config.sonarProjectKey() != null) {
            LOG.info("[collect 2/3] SonarCloud pre-scan ({} dataset releases)...", parseTags.size());
            try (ProgressBar bar = new ProgressBar("Sonar pre-scan", parseTags.size())) {
                sonarPreScanService.scanMissingReleases(owner, repo, parseTags, config.sonarProjectKey(), bar);
            } catch (SonarException e) {
                LOG.warn("SonarCloud pre-scan skipped: {}", e.getMessage());
            }
        }
        Map<String, Map<String, Integer>> sonarSmellsByTag;
        try (ProgressBar bar = new ProgressBar("Sonar index", allTags.size())) {
            sonarSmellsByTag = buildSonarSmellsByTag(plan, config, bar);
        }
        Set<String> diagSonarPaths = new HashSet<>();
        sonarSmellsByTag.values().forEach(m -> diagSonarPaths.addAll(m.keySet()));
        LOG.info("[diag] SonarCloud indexed {} distinct smell paths; samples: {}",
                diagSonarPaths.size(), diagSonarPaths.stream().limit(3).toList());

        LOG.info("[collect 3/3] Downloading & parsing sources ({} releases)...", parseTags.size());
        Map<String, List<DatasetClassData>> parsedClassesByTag;
        try (ProgressBar bar = new ProgressBar("Parse sources", parseTags.size())) {
            parsedClassesByTag = collectParsedClasses(owner, repo, parseTags, bar);
        }

        // ---- EMIT PER VARIANT ----
        List<Combo> combos = allCombos();
        LOG.info("[generate] Producing {} dataset variants (offline)...", combos.size());
        int index = 0;
        for (Combo combo : combos) {
            index++;
            LOG.info("[variant {}/{}] {}", index, combos.size(), combo.tag());
            emitVariant(plan, combo, rawCommitsByTag, sonarSmellsByTag, parsedClassesByTag);
        }
        LOG.info("[OK] {} variants written to output/batch/", combos.size());
    }

    /**
     * Fetches raw commit snapshots for every release once (the rate-limited GitHub work).
     */
    private Map<String, RawReleaseCommits> collectRawCommits(String owner, String repo, List<String> tags, ProgressBar bar) {
        Map<String, RawReleaseCommits> byTag = new LinkedHashMap<>();
        for (int idx = 0; idx < tags.size(); idx++) {
            String tag = tags.get(idx);
            String prevTag = idx > 0 ? tags.get(idx - 1) : null;
            try {
                byTag.put(tag, gitService.fetchRawReleaseCommits(owner, repo, prevTag, tag));
                bar.step(tag);
            } catch (IOException e) {
                throw new ReleaseException("I/O error fetching commits for " + tag, e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ReleaseException("Interrupted fetching commits for " + tag, e);
            }
        }
        return byTag;
    }

    /**
     * Downloads and parses the sources of each release once (flag-independent product metrics).
     */
    private Map<String, List<DatasetClassData>> collectParsedClasses(String owner, String repo, List<String> tags, ProgressBar bar) {
        Map<String, List<DatasetClassData>> byTag = new LinkedHashMap<>();
        for (String tag : tags) {
            try {
                byTag.put(tag, releaseExecutionService.parseRelease(owner, repo, tag));
            } catch (JavaParsingException e) {
                LOG.warn("Parse skipped for {}: {}", tag, e.getMessage());
                byTag.put(tag, List.of());
            }
            bar.step(tag);
        }
        return byTag;
    }

    /**
     * Derives one dataset variant from the cached raw data (no network calls).
     */
    private void emitVariant(ReleasePlan plan, Combo combo,
                             Map<String, RawReleaseCommits> rawCommitsByTag,
                             Map<String, Map<String, Integer>> sonarSmellsByTag,
                             Map<String, List<DatasetClassData>> parsedClassesByTag) throws CSVException {
        List<String> allTags = plan.timeline().orderedTags();
        List<String> selectedTags = variantSelector.selectFirstPercent(allTags, combo.percentage());
        List<JiraSnapshot> ticketsForLabeling = union(plan.resolvedTickets(), combo.useGithub() ? plan.ghTickets() : List.of());
        List<JiraSnapshot> ticketsForTlp = union(plan.allTickets(), combo.useGithub() ? plan.ghTickets() : List.of());

        Map<String, GitReleaseSnapshot> commitDataByTag = new LinkedHashMap<>();
        List<ReleaseSnapshot> history = new ArrayList<>();
        for (int idx = 0; idx < allTags.size(); idx++) {
            String tag = allTags.get(idx);
            String prevTag = idx > 0 ? allTags.get(idx - 1) : null;
            GitReleaseSnapshot cd = gitService.aggregate(rawCommitsByTag.get(tag), combo.useGithub());
            commitDataByTag.put(tag, cd);
            history.add(new ReleaseSnapshot(tag, prevTag, cd));
        }

        ReleaseLabeling labelIndex = new HistoricalBugTaker()
                .build(plan.timeline(), selectedTags, ticketsForLabeling, history, combo.proportion());

        Map<String, JiraSnapshot> ticketsByKey = indexTicketsByKey(ticketsForTlp);
        Map<String, Integer> openTicketsByRelease = computeOpenTicketsByRelease(plan.timeline(), ticketsForTlp);
        Map<String, Set<String>> ticketTouchedPaths = computeTicketTouchedPaths(history);
        Map<String, List<String>> orderedTicketsByRelease = computeOrderedTicketsByRelease(plan.timeline(), ticketsForTlp);

        Path csvPath = Paths.get("output", "batch", plan.repo() + "_" + combo.tag() + ".csv");
        BufferedWriter writer = csvWriter.open(csvPath);
        try {
            Map<String, DatasetRow> prevData = new HashMap<>();
            StoreReleaseInMemory historyStore = new StoreReleaseInMemory();
            for (String tag : allTags) {
                if (!selectedTags.contains(tag)) {
                    continue;
                }
                List<DatasetClassData> raw = parsedClassesByTag.getOrDefault(tag, List.of());
                ReleaseToDatasetRequest request = new ReleaseToDatasetRequest(
                        null, plan.repo(), tag, commitDataByTag.get(tag),
                        prevData, historyStore, labelIndex,
                        sonarSmellsByTag.getOrDefault(tag, Map.of()),
                        combo.excludeChurnZero(), ticketsByKey,
                        openTicketsByRelease.getOrDefault(tag, 0),
                        ticketTouchedPaths, orderedTicketsByRelease.getOrDefault(tag, List.of()));
                List<DatasetClassData> rows = releaseExecutionService.enrich(raw, request);
                prevData.clear();
                for (DatasetClassData row : rows) {
                    prevData.put(row.getUniqueKey(), row);
                }
                csvWriter.append(writer, rows);
            }
        } finally {
            closeVariantWriter(writer);
        }

        try {
            milestoneAuditService.write(csvPath, plan.timeline().size(), selectedTags.size(),
                    labelIndex.summary(), computeLinkageRate(history));
        } catch (IOException e) {
            LOG.warn("Audit write failed for {}: {}", csvPath.getFileName(), e.getMessage());
        }
    }

    /** Closes a variant CSV writer, wrapping failures. */
    private void closeVariantWriter(BufferedWriter writer) throws CSVException {
        try {
            writer.close();
        } catch (IOException e) {
            throw new CSVException("Failed to close variant CSV", e);
        }
    }

    /** Concatenates two ticket lists into a new mutable list. */
    private List<JiraSnapshot> union(List<JiraSnapshot> primary, List<JiraSnapshot> extra) {
        List<JiraSnapshot> result = new ArrayList<>(primary);
        result.addAll(extra);
        return result;
    }

    /** Enumerates the 2^4 flag combinations. */
    private List<Combo> allCombos() {
        List<Combo> combos = new ArrayList<>();
        for (int pct : SNORING_PERCENTAGES) {
            for (Proportion.Variant prop : Proportion.Variant.values()) {
                for (boolean gh : new boolean[]{false, true}) {
                    for (boolean churn : new boolean[]{false, true}) {
                        combos.add(new Combo(pct, prop, gh, churn));
                    }
                }
            }
        }
        return combos;
    }

    /** One experimental configuration (a point in the ablation matrix). */
    private record Combo(int percentage, Proportion.Variant proportion, boolean useGithub, boolean excludeChurnZero) {
        String tag() {
            return "pct" + percentage
                    + "_" + proportion.name().toLowerCase()
                    + "_gh" + (useGithub ? 1 : 0)
                    + "_churn" + (excludeChurnZero ? 1 : 0);
        }
    }

    /**
     * Indexes all resolved tickets by their issue key for TLP aggregation.
     *
     * @param tickets all resolved tickets (any type)
     * @return ticket index keyed by issue key
     */
    private Map<String, JiraSnapshot> indexTicketsByKey(List<JiraSnapshot> tickets) {
        Map<String, JiraSnapshot> index = new HashMap<>();
        for (JiraSnapshot ticket : tickets) {
            index.put(ticket.key(), ticket);
        }
        return index;
    }

    /**
     * Counts, for each release, the tickets already created but not yet resolved at the release
     * snapshot date (TLP "Open Tickets" workload proxy). Computed at the release date to avoid
     * data leakage from the future.
     *
     * @param plan release plan carrying the timeline dates and all tickets
     * @return open-ticket count keyed by release tag
     */
    private Map<String, Integer> computeOpenTicketsByRelease(ReleasePlan plan) {
        return computeOpenTicketsByRelease(plan.timeline(), plan.allTickets());
    }

    private Map<String, Integer> computeOpenTicketsByRelease(ReleaseTimeline timeline, List<JiraSnapshot> tickets) {
        Map<String, Integer> open = new LinkedHashMap<>();
        Map<String, java.time.Instant> tagDates = timeline.tagDates();
        for (String tag : timeline.orderedTags()) {
            java.time.Instant releaseDate = tagDates.get(tag);
            int count = 0;
            if (releaseDate != null) {
                for (JiraSnapshot ticket : tickets) {
                    boolean createdByNow = ticket.createdDate() != null && !ticket.createdDate().isAfter(releaseDate);
                    boolean stillOpen = ticket.resolvedDate() == null || ticket.resolvedDate().isAfter(releaseDate);
                    if (createdByNow && stillOpen) {
                        count++;
                    }
                }
            }
            open.put(tag, count);
        }
        return open;
    }

    /**
     * Inverts the per-release commit linkage into a ticket -> touched paths map (full history),
     * used by the Temporal Locality (TLCC) feature.
     *
     * @param releaseHistory preloaded release snapshots
     * @return map of issue key -> set of touched relative paths
     */
    private Map<String, Set<String>> computeTicketTouchedPaths(List<ReleaseSnapshot> releaseHistory) {
        Map<String, Set<String>> touched = new HashMap<>();
        for (ReleaseSnapshot snapshot : releaseHistory) {
            snapshot.commitData().fileToIssueKeys().forEach((path, keys) -> {
                for (String key : keys) {
                    touched.computeIfAbsent(key, ignored -> new HashSet<>()).add(path);
                }
            });
        }
        return touched;
    }

    /**
     * Builds, per release, the chronological sequence of ticket keys implemented up to the release
     * snapshot date (oldest first). Used as the TLCC observation window; excludes future tickets.
     *
     * @param plan release plan carrying tickets and timeline dates
     * @return map of release tag -> ordered ticket keys
     */
    private Map<String, List<String>> computeOrderedTicketsByRelease(ReleasePlan plan) {
        return computeOrderedTicketsByRelease(plan.timeline(), plan.allTickets());
    }

    private Map<String, List<String>> computeOrderedTicketsByRelease(ReleaseTimeline timeline, List<JiraSnapshot> tickets) {
        List<JiraSnapshot> sorted = new ArrayList<>(tickets);
        sorted.sort(Comparator.comparing(this::effectiveDate));
        Map<String, java.time.Instant> tagDates = timeline.tagDates();
        Map<String, List<String>> byRelease = new LinkedHashMap<>();
        for (String tag : timeline.orderedTags()) {
            java.time.Instant date = tagDates.get(tag);
            List<String> seq = new ArrayList<>();
            if (date != null) {
                for (JiraSnapshot ticket : sorted) {
                    if (!effectiveDate(ticket).isAfter(date)) {
                        seq.add(ticket.key());
                    }
                }
            }
            byRelease.put(tag, seq);
        }
        return byRelease;
    }

    /**
     * Returns the effective chronological date of a ticket (resolution date when present, else creation).
     *
     * @param ticket resolved ticket
     * @return effective ordering instant
     */
    private java.time.Instant effectiveDate(JiraSnapshot ticket) {
        return ticket.resolvedDate() != null ? ticket.resolvedDate() : ticket.createdDate();
    }

    /**
     * Closes every open CSV writer, aggregating the first close failure when needed.
     *
     * @param contexts project contexts holding the open writers
     * @throws CSVException when at least one writer cannot be closed
     */
    private void closeContexts(List<SharedStatus> contexts) throws CSVException {
        CSVException failure = null;
        for (SharedStatus context : contexts) {
            try {
                context.writer().close();
            } catch (IOException exception) {
                if (failure == null) {
                    failure = new CSVException("Failed to close CSV writer", exception);
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
    }
}
