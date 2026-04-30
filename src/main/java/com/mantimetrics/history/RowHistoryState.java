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
 * @param maxStmtCount maximum statement count observed across all releases
 * @param maxDistinctOperators maximum distinct Halstead operators observed across all releases
 * @param maxDistinctOperands maximum distinct Halstead operands observed across all releases
 * @param maxTotalOperators maximum total Halstead operators observed across all releases
 * @param maxTotalOperands maximum total Halstead operands observed across all releases
 * @param maxVocabulary maximum Halstead vocabulary observed across all releases
 * @param maxLength maximum Halstead length observed across all releases
 * @param maxVolume maximum Halstead volume observed across all releases
 * @param maxDifficulty maximum Halstead difficulty observed across all releases
 * @param maxEffort maximum Halstead effort observed across all releases
 * @param maxNestingDepth maximum nesting depth observed across all releases
 * @param everLongMethod whether the entity was ever flagged as long method
 * @param everGodClass whether the entity was ever flagged as God Class
 * @param everFeatureEnvy whether the entity was ever flagged as Feature Envy
 * @param everDuplicatedCode whether the entity was ever flagged as duplicated code
 * @param maxCodeSmells maximum PMD violation count observed across all releases
 * @param maxSmellDensity maximum smell density (NSmells/LOC) observed across all releases
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
        int maxNSmells,
        int maxStmtCount,
        int maxDistinctOperators,
        int maxDistinctOperands,
        int maxTotalOperators,
        int maxTotalOperands,
        double maxVocabulary,
        double maxLength,
        double maxVolume,
        double maxDifficulty,
        double maxEffort,
        int maxNestingDepth,
        boolean everLongMethod,
        boolean everGodClass,
        boolean everFeatureEnvy,
        boolean everDuplicatedCode,
        int maxCodeSmells,
        double maxSmellDensity
) {
    /**
     * Normalizes the author list to a distinct, immutable encounter-ordered collection.
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
