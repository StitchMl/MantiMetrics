package com.mantimetrics.labeling;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Release-aware lookup of buggy file paths produced from the full Git/JIRA history.
 */
public final class HistoricalBugLabelIndex {
    private final Map<String, Set<String>> buggyPathsByRelease;
    private final Summary summary;

    public HistoricalBugLabelIndex(Map<String, Set<String>> buggyPathsByRelease, Summary summary) {
        this.buggyPathsByRelease = Map.copyOf(Objects.requireNonNull(buggyPathsByRelease, "buggyPathsByRelease"));
        this.summary = Objects.requireNonNull(summary, "summary");
    }

    public boolean isBuggy(String releaseId, String relativePath) {
        return buggyPathsByRelease.getOrDefault(releaseId, Set.of()).contains(relativePath);
    }

    public Summary summary() {
        return summary;
    }

    /**
     * Metadata emitted in the audit file so the dataset makes the chosen historical oracle explicit.
     */
    public record Summary(
            String strategy,
            int totalResolvedTickets,
            int ticketsWithFixCommit,
            int ticketsUsingAffectedVersions,
            int ticketsUsingTotalFallback,
            int labelingReleaseCount,
            int datasetReleaseCount,
            String notes
    ) {
    }
}
