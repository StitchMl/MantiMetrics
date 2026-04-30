package com.mantimetrics.model;

import com.mantimetrics.metrics.MethodMetrics;

import java.util.List;

/**
 * Shared immutable payload used by both class-level and method-level dataset rows.
 *
 * @param projectName analyzed project name
 * @param path normalized relative source path
 * @param releaseId release identifier
 * @param metrics static metrics computed for the entity
 * @param commitHashes commits touching the entity in the current release range
 * @param buggy whether the entity is historically labeled as buggy in the current release
 * @param codeSmells PMD code smell count for the entity
 * @param touches commits touching the entity in the current release
 * @param totalTouches cumulative touches across releases
 * @param issueTouches issue-linked touches in the current release
 * @param totalIssueTouches cumulative issue-linked touches across releases
 * @param authors distinct authors in the current release
 * @param totalAuthors cumulative distinct authors across releases
 * @param addedLines lines added in the current release
 * @param deletedLines lines deleted in the current release
 * @param churn sum of added and deleted lines in the current release
 * @param totalChurn cumulative churn across releases
 * @param prevCodeSmells code smell count observed in the previous release
 * @param prevBuggy whether the entity was buggy in the previous release
 * @param ageInReleases number of analyzed releases in which the entity exists
 * @param startLine inclusive start line of the entity
 * @param endLine inclusive end line of the entity
 * @param maxLoc maximum LOC seen across all releases (asterisk feature)
 * @param maxCyclomatic maximum cyclomatic complexity seen across all releases (asterisk feature)
 * @param maxCognitive maximum cognitive complexity seen across all releases (asterisk feature)
 * @param maxNSmells maximum total smell count seen across all releases (asterisk feature)
 * @param maxStmtCount maximum statement count seen across all releases
 * @param maxDistinctOperators maximum distinct Halstead operators seen across all releases
 * @param maxDistinctOperands maximum distinct Halstead operands seen across all releases
 * @param maxTotalOperators maximum total Halstead operators seen across all releases
 * @param maxTotalOperands maximum total Halstead operands seen across all releases
 * @param maxVocabulary maximum Halstead vocabulary seen across all releases
 * @param maxLength maximum Halstead length seen across all releases
 * @param maxVolume maximum Halstead volume seen across all releases
 * @param maxDifficulty maximum Halstead difficulty seen across all releases
 * @param maxEffort maximum Halstead effort seen across all releases
 * @param maxNestingDepth maximum nesting depth seen across all releases
 * @param everLongMethod whether the entity was ever flagged as long method
 * @param everGodClass whether the entity was ever flagged as God Class
 * @param everFeatureEnvy whether the entity was ever flagged as Feature Envy
 * @param everDuplicatedCode whether the entity was ever flagged as duplicated code
 * @param maxCodeSmells maximum PMD violation count seen across all releases
 * @param maxSmellDensity maximum smell density seen across all releases
 */
record MetricDatasetRowData(String projectName, String path, String releaseId, MethodMetrics metrics,
                            List<String> commitHashes, boolean buggy, int codeSmells, int touches, int totalTouches,
                            int issueTouches, int totalIssueTouches, int authors, int totalAuthors, int addedLines,
                            int deletedLines, int churn, int totalChurn, int prevCodeSmells, boolean prevBuggy,
                            int ageInReleases, int startLine, int endLine,
                            int maxLoc, int maxCyclomatic, int maxCognitive, int maxNSmells,
                            int maxStmtCount, int maxDistinctOperators, int maxDistinctOperands,
                            int maxTotalOperators, int maxTotalOperands,
                            double maxVocabulary, double maxLength, double maxVolume,
                            double maxDifficulty, double maxEffort, int maxNestingDepth,
                            boolean everLongMethod, boolean everGodClass,
                            boolean everFeatureEnvy, boolean everDuplicatedCode,
                            int maxCodeSmells, double maxSmellDensity) {
    /**
     * Creates an immutable shared dataset payload, copying the commit hash list defensively.
     */
    MetricDatasetRowData(
            String projectName,
            String path,
            String releaseId,
            MethodMetrics metrics,
            List<String> commitHashes,
            boolean buggy,
            int codeSmells,
            int touches,
            int totalTouches,
            int issueTouches,
            int totalIssueTouches,
            int authors,
            int totalAuthors,
            int addedLines,
            int deletedLines,
            int churn,
            int totalChurn,
            int prevCodeSmells,
            boolean prevBuggy,
            int ageInReleases,
            int startLine,
            int endLine,
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
        this.projectName = projectName;
        this.path = path;
        this.releaseId = releaseId;
        this.metrics = metrics;
        this.commitHashes = List.copyOf(commitHashes);
        this.buggy = buggy;
        this.codeSmells = codeSmells;
        this.touches = touches;
        this.totalTouches = totalTouches;
        this.issueTouches = issueTouches;
        this.totalIssueTouches = totalIssueTouches;
        this.authors = authors;
        this.totalAuthors = totalAuthors;
        this.addedLines = addedLines;
        this.deletedLines = deletedLines;
        this.churn = churn;
        this.totalChurn = totalChurn;
        this.prevCodeSmells = prevCodeSmells;
        this.prevBuggy = prevBuggy;
        this.ageInReleases = ageInReleases;
        this.startLine = startLine;
        this.endLine = endLine;
        this.maxLoc = maxLoc;
        this.maxCyclomatic = maxCyclomatic;
        this.maxCognitive = maxCognitive;
        this.maxNSmells = maxNSmells;
        this.maxStmtCount = maxStmtCount;
        this.maxDistinctOperators = maxDistinctOperators;
        this.maxDistinctOperands = maxDistinctOperands;
        this.maxTotalOperators = maxTotalOperators;
        this.maxTotalOperands = maxTotalOperands;
        this.maxVocabulary = maxVocabulary;
        this.maxLength = maxLength;
        this.maxVolume = maxVolume;
        this.maxDifficulty = maxDifficulty;
        this.maxEffort = maxEffort;
        this.maxNestingDepth = maxNestingDepth;
        this.everLongMethod = everLongMethod;
        this.everGodClass = everGodClass;
        this.everFeatureEnvy = everFeatureEnvy;
        this.everDuplicatedCode = everDuplicatedCode;
        this.maxCodeSmells = maxCodeSmells;
        this.maxSmellDensity = maxSmellDensity;
    }

    /**
     * Returns the total smell count combining PMD violations and binary smell detectors.
     *
     * @return total smell count
     */
    int nSmells() {
        int binarySmells = 0;
        if (metrics.isLongMethod()) binarySmells++;
        if (metrics.isGodClass()) binarySmells++;
        if (metrics.isFeatureEnvy()) binarySmells++;
        if (metrics.isDuplicatedCode()) binarySmells++;
        return codeSmells + binarySmells;
    }
}
