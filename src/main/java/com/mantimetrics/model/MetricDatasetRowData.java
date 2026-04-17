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
 */
record MetricDatasetRowData(String projectName, String path, String releaseId, MethodMetrics metrics,
                            List<String> commitHashes, boolean buggy, int codeSmells, int touches, int totalTouches,
                            int issueTouches, int totalIssueTouches, int authors, int totalAuthors, int addedLines,
                            int deletedLines, int churn, int totalChurn, int prevCodeSmells, boolean prevBuggy,
                            int ageInReleases, int startLine, int endLine) {
    /**
     * Creates an immutable shared dataset payload, copying the commit hash list defensively.
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
            int endLine
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
