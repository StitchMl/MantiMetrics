package com.mantimetrics.model;

import com.mantimetrics.metrics.MethodMetrics;

import java.util.List;

final class MetricDatasetRowData {
    private final String projectName;
    private final String path;
    private final String releaseId;
    private final MethodMetrics metrics;
    private final List<String> commitHashes;
    private final boolean buggy;
    private final int codeSmells;
    private final int touches;
    private final int prevCodeSmells;
    private final boolean prevBuggy;
    private final int startLine;
    private final int endLine;

    MetricDatasetRowData(
            String projectName,
            String path,
            String releaseId,
            MethodMetrics metrics,
            List<String> commitHashes,
            boolean buggy,
            int codeSmells,
            int touches,
            int prevCodeSmells,
            boolean prevBuggy,
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
        this.prevCodeSmells = prevCodeSmells;
        this.prevBuggy = prevBuggy;
        this.startLine = startLine;
        this.endLine = endLine;
    }

    String projectName() { return projectName; }
    String path() { return path; }
    String releaseId() { return releaseId; }
    MethodMetrics metrics() { return metrics; }
    List<String> commitHashes() { return commitHashes; }
    boolean buggy() { return buggy; }
    int codeSmells() { return codeSmells; }
    int touches() { return touches; }
    int prevCodeSmells() { return prevCodeSmells; }
    boolean prevBuggy() { return prevBuggy; }
    int startLine() { return startLine; }
    int endLine() { return endLine; }

    int nSmells() {
        int binarySmells = 0;
        if (metrics.isLongMethod()) binarySmells++;
        if (metrics.isGodClass()) binarySmells++;
        if (metrics.isFeatureEnvy()) binarySmells++;
        if (metrics.isDuplicatedCode()) binarySmells++;
        return codeSmells + binarySmells;
    }
}
