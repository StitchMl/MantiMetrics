package com.mantimetrics.analysis;

import com.mantimetrics.csv.CSVWriter;
import com.mantimetrics.csv.CsvWriteException;
import com.mantimetrics.dataset.DatasetArtifactService;
import com.mantimetrics.git.GitService;
import com.mantimetrics.git.ProjectConfig;
import com.mantimetrics.history.RowHistoryStore;
import com.mantimetrics.jira.JiraClientException;
import com.mantimetrics.labeling.HistoricalBugLabelIndex;
import com.mantimetrics.labeling.HistoricalBugLabelIndexBuilder;
import com.mantimetrics.audit.MilestoneAuditService;
import com.mantimetrics.release.ReleaseProcessingException;
import com.mantimetrics.sonar.SonarCloudClient;
import com.mantimetrics.sonar.SonarCloudException;
import com.mantimetrics.sonar.SonarPreScanService;
import com.mantimetrics.sonar.SonarSmellIndex;
import com.mantimetrics.util.ProgressBar;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Project-level orchestrator. It preloads the full release history once, builds the historical bug oracle,
 * then writes one raw dataset per requested granularity.
 */
public final class ProjectProcessor {
 private static final org.slf4j.Logger LOG =
 org.slf4j.LoggerFactory.getLogger(ProjectProcessor.class);

 private final ProjectReleasePlanner releasePlanner;
 private final ReleaseExecutionService releaseExecutionService;
 private final GitService gitService;
 private final CSVWriter csvWriter;
 private final DatasetArtifactService datasetArtifactService;
 private final MilestoneAuditService milestoneAuditService;
 private final SonarCloudClient sonarCloudClient;
 private final SonarPreScanService sonarPreScanService;

 /**
 * Creates the project processor with all collaborators needed to execute the full release pipeline.
 *
 * @param releasePlanner planner that resolves the common release timeline
 * @param releaseExecutionService service that executes one prepared release
 * @param gitService Git service used to preload release commit data
 * @param csvWriter CSV writer used to open per-granularity output files
 * @param sonarPreScanService service that runs {@code mvn sonar:sonar} for missing releases
 * @param sonarCloudClient authenticated SonarCloud client shared across the pipeline
 * @param outputServices groups {@link DatasetArtifactService} and {@link MilestoneAuditService}
 */
 public ProjectProcessor(
 ProjectReleasePlanner releasePlanner,
 ReleaseExecutionService releaseExecutionService,
 GitService gitService,
 CSVWriter csvWriter,
 SonarPreScanService sonarPreScanService,
 SonarCloudClient sonarCloudClient,
 ProjectOutputServices outputServices
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
 * @param granularities dataset granularities to generate
 * @throws JiraClientException when Jira metadata cannot be loaded
 * @throws CsvWriteException when a dataset CSV file cannot be written or closed
 */
 public void process(ProjectConfig config, List<Granularity> granularities)
 throws JiraClientException, CsvWriteException {
 ProjectReleasePlan plan = releasePlanner.plan(config);
 if (plan == null) {
 return;
 }

 LOG.info("┌─────────────────────────────────────────────────────────────────────");
 LOG.info("│ Project : {}", config.name());
 LOG.info("│ Releases : {} total → {} selected ({}%) │ {} bug tickets",
 plan.timeline().size(), plan.selectedTags().size(),
 config.percentage(), plan.resolvedTickets().size());
 LOG.info("└─────────────────────────────────────────────────────────────────────");

 // ── Phase 1: Git commit history ──────────────────────────────────────
 LOG.info("[1/5] Preloading Git commit history ({} releases)…", plan.timeline().size());
 List<ReleaseSnapshot> releaseHistory;
 try (ProgressBar bar = new ProgressBar("Git history", plan.timeline().size())) {
 releaseHistory = buildReleaseHistory(plan, bar);
 }
 LOG.info("[1/5] done — {} snapshots loaded", releaseHistory.size());

 // ── Phase 2: Bug-label oracle (sub-bars managed inside the builder) ──
 LOG.info("[2/5] Building bug-label oracle ({} tickets, Proportion-Total)…",
 plan.resolvedTickets().size());
 HistoricalBugLabelIndex labelIndex = new HistoricalBugLabelIndexBuilder()
 .build(plan.timeline(), plan.selectedTags(), plan.resolvedTickets(), releaseHistory);
 LOG.info("[2/5] done — linked={}, IV-JIRA={}, Proportion-fallback={}",
 labelIndex.summary().ticketsWithFixCommit(),
 labelIndex.summary().ticketsUsingAffectedVersions(),
 labelIndex.summary().ticketsUsingTotalFallback());

 // ── Phase 3a: SonarCloud per-release pre-scan ─────────────────────────
 if (config.sonarProjectKey() != null) {
 int total = plan.timeline().size();
 LOG.info("[3a/5] SonarCloud pre-scan — {} releases (skips already-scanned)…", total);
 try (ProgressBar bar = new ProgressBar("Sonar pre-scan", total)) {
 int newScans = sonarPreScanService.scanMissingReleases(
 plan.owner(), plan.repo(),
 plan.timeline().orderedTags(),
 config.sonarProjectKey(), bar);
 LOG.info("[3a/5] done — {} new releases scanned", newScans);
 } catch (SonarCloudException e) {
 LOG.warn("[3a/5] SonarCloud pre-scan skipped: {}", e.getMessage());
 }
 }

 // ── Phase 3b: Build SonarCloud smell index ────────────────────────────
 String sonarLabel = config.sonarProjectKey() != null
 ? config.sonarProjectKey() : "n/a";
 LOG.info("[3b/5] Building SonarCloud smell index — {}…", sonarLabel);
 Map<String, Map<String, Integer>> sonarSmellsByTag;
 try (ProgressBar bar = new ProgressBar("Sonar index", plan.timeline().size())) {
 sonarSmellsByTag = buildSonarSmellsByTag(plan, config, bar);
 }
 LOG.info("[3b/5] done");

 // ── Phase 4: Dataset generation ───────────────────────────────────────
 int releasesTotal = plan.selectedTags().size();
 LOG.info("[5/5] Generating dataset — {} releases…", releasesTotal);
 Map<Granularity, Path> csvPaths = new LinkedHashMap<>();
 List<ProjectContext> contexts = openContexts(plan, granularities, csvPaths, labelIndex,
 sonarSmellsByTag);
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
 LOG.info("✓ Dataset complete — output files written to output/");
 }

 /**
 * Builds the per-tag SonarCloud file-smell maps.
 *
 * <p>For each tag the lookup strategy is:
 * <ol>
 * <li>Exact version match ({@code sonar.projectVersion == tag}) — populated by the pre-scan phase.</li>
 * <li>Date-based fallback — latest analysis whose date ≤ release date, or earliest available proxy.</li>
 * </ol>
 * Returns an empty map gracefully when SonarCloud is not configured or the API is unavailable.
 *
 * @param plan release plan providing the tag timeline
 * @param config project configuration carrying the optional SonarCloud key
 * @param bar progress bar stepped once per tag
 * @return map of release tag → (file-path → smell-count)
 */
 private Map<String, Map<String, Integer>> buildSonarSmellsByTag(
 ProjectReleasePlan plan, ProjectConfig config, ProgressBar bar) {
 SonarSmellIndex sonarIndex = SonarSmellIndex.EMPTY;
 if (config.sonarProjectKey() != null && !config.sonarProjectKey().isBlank()) {
 try {
 sonarIndex = SonarSmellIndex.build(sonarCloudClient, config.sonarProjectKey());
 } catch (SonarCloudException e) {
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
 private List<ProjectContext> openContexts(
 ProjectReleasePlan plan,
 List<Granularity> granularities,
 Map<Granularity, Path> csvPaths,
 HistoricalBugLabelIndex labelIndex,
 Map<String, Map<String, Integer>> sonarSmellsByTag
 ) throws CsvWriteException {
 List<ProjectContext> contexts = new ArrayList<>();
 try {
 for (Granularity granularity : granularities) {
 Path csvPath = Paths.get("output", plan.repo() + "_dataset_" + granularity.name().toLowerCase() + ".csv");
 BufferedWriter writer = csvWriter.open(csvPath, granularity);
 csvPaths.put(granularity, csvPath);
 contexts.add(new ProjectContext(
 plan.owner(),
 plan.repo(),
 granularity,
 csvWriter,
 new HashMap<>(),
 new RowHistoryStore(),
 labelIndex,
 writer,
 sonarSmellsByTag
 ));
 }
 return contexts;
 } catch (CsvWriteException exception) {
 try {
 closeContexts(contexts);
 } catch (CsvWriteException closeFailure) {
 exception.addSuppressed(closeFailure);
 }
 throw exception;
 }
 }

 /**
 * Preloads the complete release history, including the releases excluded by snoring, because those future
 * fix commits are still needed to label the older dataset rows.
 */
 private List<ReleaseSnapshot> buildReleaseHistory(ProjectReleasePlan plan, ProgressBar bar) {
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
 gitService.buildReleaseCommitData(plan.owner(), plan.repo(), previousTag, tag)
 ));
 bar.step(tag);
 } catch (IOException exception) {
 throw new ReleaseProcessingException("I/O error while preloading commit history for " + tag, exception);
 } catch (InterruptedException exception) {
 Thread.currentThread().interrupt();
 throw new ReleaseProcessingException("Interrupted while preloading commit history for " + tag, exception);
 }
 }
 return history;
 }

 /**
 * Generates the derived artifacts and audit file for every produced raw dataset.
 *
 * @param csvPaths raw dataset paths grouped by granularity
 * @param plan release plan associated with the project
 * @param labelIndex historical bug-label index used during labeling
 */
 private void generateArtifacts(
 Map<Granularity, Path> csvPaths,
 ProjectReleasePlan plan,
 HistoricalBugLabelIndex labelIndex,
 List<ReleaseSnapshot> releaseHistory
 ) {
 double linkageRate = computeLinkageRate(releaseHistory);
 for (Path csvPath : csvPaths.values()) {
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
 throw new ReleaseProcessingException("I/O error when generating derived dataset artifacts", exception);
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
 * Closes every open CSV writer, aggregating the first close failure when needed.
 *
 * @param contexts project contexts holding the open writers
 * @throws CsvWriteException when at least one writer cannot be closed
 */
 private void closeContexts(List<ProjectContext> contexts) throws CsvWriteException {
 CsvWriteException failure = null;
 for (ProjectContext context : contexts) {
 try {
 context.writer().close();
 } catch (IOException exception) {
 if (failure == null) {
 failure = new CsvWriteException("Failed to close CSV writer", exception);
 }
 }
 }
 if (failure != null) {
 throw failure;
 }
 }
}
