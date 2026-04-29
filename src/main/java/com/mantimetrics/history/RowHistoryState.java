package com.mantimetrics.history;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/**
 * Cumulative, release-to-release historical state for a dataset entity.
 *
 * @param totalTouches total number of commits touching the entity up to the current release
 * @param totalIssueTouches total number of issue-linked touches up to the current release
 * @param totalChurn total churn accumulated up to the current release
 * @param authors distinct authors seen up to the current release
 * @param ageInReleases number of analyzed releases in which the entity has existed
 * @param maxLoc maximum LOC observed across all releases up to and including the current one
 * @param maxCyclomatic maximum cyclomatic complexity observed across all releases
 * @param maxCognitive maximum cognitive complexity observed across all releases
 * @param maxNSmells maximum total smell count (PMD + binary detectors) observed across all releases
 */
public record RowHistoryState(
        int totalTouches,
        int totalIssueTouches,
        int totalChurn,
        List<String> authors,
        int ageInReleases,
        int maxLoc,
        int maxCyclomatic,
        int maxCognitive,
        int maxNSmells
) {
    /**
     * Normalizes the author list to a distinct, immutable encounter-ordered collection.
     *
     * @param totalTouches total number of touches accumulated so far
     * @param totalIssueTouches total number of issue-linked touches accumulated so far
     * @param totalChurn total churn accumulated so far
     * @param authors distinct authors seen so far
     * @param ageInReleases release age of the entity
     * @param maxLoc maximum LOC seen so far
     * @param maxCyclomatic maximum cyclomatic complexity seen so far
     * @param maxCognitive maximum cognitive complexity seen so far
     * @param maxNSmells maximum total smell count seen so far
     */
    public RowHistoryState {
        authors = List.copyOf(new LinkedHashSet<>(Objects.requireNonNull(authors, "authors")));
    }

    /**
     * Returns the number of distinct authors seen so far.
     *
     * @return total distinct authors
     */
    public int totalAuthors() {
        return authors.size();
    }
}
