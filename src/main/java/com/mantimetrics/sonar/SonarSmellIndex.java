package com.mantimetrics.sonar;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Indexed snapshot of per-file code-smell counts across all SonarCloud analyses for a project.
 * Smells for each analysis are fetched lazily and cached on first access.
 */
public final class SonarSmellIndex {
 private static final Logger LOG = LoggerFactory.getLogger(SonarSmellIndex.class);

 /** Sentinel index that always returns an empty smell map. */
 public static final SonarSmellIndex EMPTY = new SonarSmellIndex(List.of(), null, null);

 private final List<SonarAnalysis> analyses;
 private final SonarCloudClient client;
 private final String projectKey;
 private final Map<String, Map<String, Integer>> cache = new LinkedHashMap<>();

 private SonarSmellIndex(List<SonarAnalysis> analyses, SonarCloudClient client, String projectKey) {
 this.analyses = analyses;
 this.client = client;
 this.projectKey = projectKey;
 }

 /**
 * Fetches the full analysis list for a project and returns a ready-to-use index.
 *
 * @param client SonarCloud client used for REST calls
 * @param projectKey SonarCloud project key
 * @return populated smell index
 * @throws SonarCloudException when the analyses cannot be fetched
 */
 public static SonarSmellIndex build(SonarCloudClient client, String projectKey)
 throws SonarCloudException {
 List<SonarAnalysis> analyses = client.fetchAnalyses(projectKey);
 LOG.info("SonarCloud {} - {} analyses available for smell mapping", projectKey, analyses.size());
 return new SonarSmellIndex(analyses, client, projectKey);
 }

 /**
 * Returns the file-level code-smell counts from the best matching SonarCloud analysis.
 *
 * <p>Selection strategy (analyses are sorted oldest → newest):
 * <ol>
 * <li>Prefer the <em>latest</em> analysis whose date is ≤ {@code releaseDate} — exact historical match.</li>
 * <li>If no such analysis exists (e.g. SonarCloud was set up after all release dates), fall back
 * to the <em>earliest</em> available analysis as a proxy for the project's smell profile.</li>
 * </ol>
 *
 * @param releaseDate release tag date used to pick the correct analysis snapshot
 * @return map of normalized relative path → code smell count, possibly empty
 */
 public Map<String, Integer> getSmellsForDate(Instant releaseDate) {
 if (analyses.isEmpty() || client == null) {
 return Map.of();
 }

 SonarAnalysis best = null;
 for (SonarAnalysis analysis : analyses) {
 if (!analysis.date().isAfter(releaseDate)) {
 best = analysis;
 } else {
 break;
 }
 }

 // Fallback: SonarCloud was set up after all historical release dates.
 // Use the oldest available analysis as the best available proxy.
 if (best == null) {
 best = analyses.get(0);
 LOG.debug("SonarCloud {}: no analysis predates {} — using earliest analysis {} as proxy",
 projectKey, releaseDate, best.date());
 }

 final String analysisKey = best.key();
 return cache.computeIfAbsent(analysisKey, key -> {
 try {
 Map<String, Integer> smells = client.fetchFileSmells(projectKey, key);
 LOG.debug("SonarCloud {} analysis {} -> {} files", projectKey, key, smells.size());
 return smells;
 } catch (SonarCloudException e) {
 LOG.warn("SonarCloud smell fetch failed for {}/{}: {}", projectKey, key, e.getMessage());
 return Map.of();
 }
 });
 }
}
