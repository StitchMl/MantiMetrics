package com.mantimetrics.history;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/**
 * Cumulative, release-to-release historical state for a class entity (Milestone 1 lean schema).
 *
 * @param totalTouches total commits touching the class up to the current release
 * @param totalIssueTouches total issue-linked touches up to the current release
 * @param totalChurn total churn accumulated up to the current release
 * @param authors distinct authors seen up to the current release
 * @param ageInReleases number of analyzed releases in which the class has existed
 * @param maxLoc maximum LOC observed across releases
 * @param maxWmc maximum WMC observed across releases
 * @param maxNSmells maximum NSmells observed across releases
 */
public record ComulationMetricsCalculator(
        int totalTouches,
        int totalIssueTouches,
        int totalChurn,
        List<String> authors,
        int ageInReleases,
        int maxLoc,
        int maxWmc,
        int maxNSmells
) {
    /** Normalizes authors to a distinct, immutable encounter-ordered list. */
    public ComulationMetricsCalculator {
        authors = List.copyOf(new LinkedHashSet<>(Objects.requireNonNull(authors, "authors")));
    }

    /** @return number of distinct authors seen so far */
    public int totalAuthors() {
        return authors.size();
    }
}
