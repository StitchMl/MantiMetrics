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
        Map<String, Integer> open = new LinkedHashMap<>();
        Map<String, java.time.Instant> tagDates = plan.timeline().tagDates();
        for (String tag : plan.timeline().orderedTags()) {
            java.time.Instant releaseDate = tagDates.get(tag);
            int count = 0;
            if (releaseDate != null) {
                for (JiraSnapshot ticket : plan.allTickets()) {
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
        List<JiraSnapshot> sorted = new ArrayList<>(plan.allTickets());
        sorted.sort(Comparator.comparing(this::effectiveDate));
        Map<String, java.time.Instant> tagDates = plan.timeline().tagDates();
        Map<String, List<String>> byRelease = new LinkedHashMap<>();
        for (String tag : plan.timeline().orderedTags()) {
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
