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
     * Returns the file-level code-smell counts from the latest SonarCloud analysis that predates
     * (or equals) the given release date. Returns an empty map when no matching analysis is found
     * or when the index is empty.
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
        if (best == null) {
            return Map.of();
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
