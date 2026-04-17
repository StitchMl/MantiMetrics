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

    /**
     * Creates an immutable historical bug-label index together with its audit summary.
     *
     * @param buggyPathsByRelease buggy file paths grouped by release identifier
     * @param summary summary describing how the oracle was built
     */
    public HistoricalBugLabelIndex(Map<String, Set<String>> buggyPathsByRelease, Summary summary) {
        this.buggyPathsByRelease = Map.copyOf(Objects.requireNonNull(buggyPathsByRelease, "buggyPathsByRelease"));
        this.summary = Objects.requireNonNull(summary, "summary");
    }

    /**
     * Reports whether a file path is historically labeled as buggy for a specific release.
     *
     * @param releaseId release identifier to inspect
     * @param relativePath normalized relative source path
     * @return {@code true} when the file is considered buggy in that release
     */
    public boolean isBuggy(String releaseId, String relativePath) {
        return buggyPathsByRelease.getOrDefault(releaseId, Set.of()).contains(relativePath);
    }

    /**
     * Returns the audit summary describing the labeling strategy.
     *
     * @return immutable labeling summary
     */
    public Summary summary() {
        return summary;
    }

    /**
     * Metadata emitted in the audit file so the dataset makes the chosen historical oracle explicit.
     *
     * @param strategy labeling strategy identifier
     * @param totalResolvedTickets total resolved bug tickets considered
     * @param ticketsWithFixCommit tickets for which a fixing release was identified
     * @param ticketsUsingAffectedVersions tickets resolved through Jira affected versions
     * @param ticketsUsingTotalFallback tickets resolved through the fallback Total strategy
     * @param labelingReleaseCount number of releases available in the full historical timeline
     * @param datasetReleaseCount number of releases kept for dataset generation
     * @param notes free-form notes explaining the oracle behavior
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
