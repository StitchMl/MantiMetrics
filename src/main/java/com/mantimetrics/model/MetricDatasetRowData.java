package com.mantimetrics.model;

import com.mantimetrics.metrics.MethodMetrics;

import java.util.List;

record MetricDatasetRowData(String projectName, String path, String releaseId, MethodMetrics metrics,
                            List<String> commitHashes, boolean buggy, int codeSmells, int touches, int totalTouches,
                            int issueTouches, int totalIssueTouches, int authors, int totalAuthors, int addedLines,
                            int deletedLines, int churn, int totalChurn, int prevCodeSmells, boolean prevBuggy,
                            int ageInReleases, int startLine, int endLine) {
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

    int nSmells() {
        int binarySmells = 0;
        if (metrics.isLongMethod()) binarySmells++;
        if (metrics.isGodClass()) binarySmells++;
        if (metrics.isFeatureEnvy()) binarySmells++;
        if (metrics.isDuplicatedCode()) binarySmells++;
        return codeSmells + binarySmells;
    }
}
