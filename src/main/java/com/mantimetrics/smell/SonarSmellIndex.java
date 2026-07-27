package com.mantimetrics.smell;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Indexed snapshot of per-file code-smell counts across all SonarCloud analyses for a project.
 * Smells for each analysis are fetched lazily and cached on first access.
 *
 * <p>Lookup strategy (the best accuracy first):
 * <ol>
 *   <li>{@link #getSmellsForTag(String)} — exact match on {@code sonar.projectVersion} set during
 *       each per-release scan (requires the pre-scan phase to have been run).</li>
 *   <li>{@link #getSmellsForDate(Instant)} — picks the latest analysis whose date ≤ release date;
 *       falls back to the earliest available analysis when none predates the release.</li>
 * </ol>
 */
public final class SonarSmellIndex {
    private static final Logger LOG = LoggerFactory.getLogger(SonarSmellIndex.class);

    /** Sentinel index that always returns an empty smell map. */
    public static final SonarSmellIndex EMPTY = new SonarSmellIndex(List.of(), null, null);

    private final List<SonarAnalysis>           analyses;
    private final Map<String, SonarAnalysis>    byVersion;   // projectVersion → analysis
    private final SonarClient              client;
    private final String                        projectKey;
    private final Map<String, Map<String, Integer>> cache = new LinkedHashMap<>();

    private SonarSmellIndex(List<SonarAnalysis> analyses, SonarClient client, String projectKey) {
        this.analyses   = analyses;
        this.client     = client;
        this.projectKey = projectKey;

        Map<String, SonarAnalysis> versionMap = new LinkedHashMap<>();
        for (SonarAnalysis a : analyses) {
            if (a.projectVersion() != null && !a.projectVersion().isBlank()) {
                versionMap.put(a.projectVersion(), a);
            }
        }
        this.byVersion = Map.copyOf(versionMap);
    }

    /**
     * Fetches the full analysis list for a project and returns a ready-to-use index.
     *
     * @param client     SonarCloud client used for REST calls
     * @param projectKey SonarCloud project key
     * @return populated smell index
     * @throws SonarException when the analyses cannot be fetched
     */
    public static SonarSmellIndex build(SonarClient client, String projectKey)
            throws SonarException {
        List<SonarAnalysis> analyses = client.fetchAnalyses(projectKey);
        long withVersion = analyses.stream()
                .filter(a -> a.projectVersion() != null && !a.projectVersion().isBlank())
                .count();
        LOG.info("SonarCloud {} — {} analyses ({} with version tag)", projectKey,
                analyses.size(), withVersion);
        return new SonarSmellIndex(analyses, client, projectKey);
    }

    /**
     * Returns file-level smell counts by matching the exact release tag
     * set as {@code sonar.projectVersion} during the pre-scan phase.
     * Returns an empty map when no analysis carries that version label.
     *
     * @param tag release tag (e.g. {@code release-1.8.1})
     * @return smell map, or empty if the tag was never scanned
     */
    public Map<String, Integer> getSmellsForTag(String tag) {
        if (client == null) return Map.of();
        SonarAnalysis analysis = byVersion.get(tag);
        if (analysis == null) return Map.of();
        return fetchOrCached(analysis.key());
    }

    /**
     * Returns the file-level code-smell counts from the best matching SonarCloud analysis by date.
     *
     * <p>Selection strategy (analyses are sorted oldest → newest):
     * <ol>
     *   <li>Latest analysis whose date is ≤ {@code releaseDate} — exact historical match.</li>
     *   <li>If none predates the release (SonarCloud set up after all releases),
     *       falls back to the earliest available analysis as a proxy.</li>
     * </ol>
     *
     * @param releaseDate release tag date
     * @return smell map, or empty if index is empty
     */
    public Map<String, Integer> getSmellsForDate(Instant releaseDate) {
        if (analyses.isEmpty() || client == null) return Map.of();

        SonarAnalysis best = null;
        for (SonarAnalysis a : analyses) {
            if (!a.date().isAfter(releaseDate)) {
                best = a;
            } else {
                break;
            }
        }
        if (best == null) {
            best = analyses.get(0);  // proxy: oldest available
            LOG.debug("SonarCloud {}: no analysis predates {} — using earliest ({}) as proxy",
                    projectKey, releaseDate, best.date());
        }
        return fetchOrCached(best.key());
    }

    // ── private ──────────────────────────────────────────────────────────────

    private Map<String, Integer> fetchOrCached(String analysisKey) {
        return cache.computeIfAbsent(analysisKey, key -> {
            try {
                Map<String, Integer> smells = client.fetchFileSmells(projectKey, key);
                LOG.debug("SonarCloud {} analysis {} — {} files", projectKey, key, smells.size());
                return smells;
            } catch (SonarException e) {
                LOG.warn("SonarCloud smell fetch failed for {}/{}: {}", projectKey, key, e.getMessage());
                return Map.of();
            }
        });
    }
}
