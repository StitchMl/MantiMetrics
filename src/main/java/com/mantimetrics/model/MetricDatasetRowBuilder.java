package com.mantimetrics.model;

import com.mantimetrics.metrics.MethodMetrics;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

abstract class MetricDatasetRowBuilder<T extends MetricDatasetRowBuilder<T>> {
    protected String projectName;
    protected String path;
    protected String releaseId;
    protected MethodMetrics metrics;
    protected List<String> commitHashes = Collections.emptyList();
    protected boolean buggy;
    protected int codeSmells;
    protected int touches;
    protected int prevCodeSmells;
    protected boolean prevBuggy;
    protected int startLine;
    protected int endLine;

    public final T projectName(String value) {
        this.projectName = Objects.requireNonNull(value, "projectName");
        return self();
    }

    public final T path(String value) {
        this.path = Objects.requireNonNull(value, "path");
        return self();
    }

    public final T releaseId(String value) {
        this.releaseId = Objects.requireNonNull(value, "releaseId");
        return self();
    }

    public final T metrics(MethodMetrics value) {
        this.metrics = Objects.requireNonNull(value, "metrics");
        return self();
    }

    public final T commitHashes(List<String> value) {
        this.commitHashes = List.copyOf(Objects.requireNonNull(value, "commitHashes"));
        return self();
    }

    public final T buggy(boolean value) {
        this.buggy = value;
        return self();
    }

    public final T codeSmells(int value) {
        this.codeSmells = value;
        return self();
    }

    public final T touches(int value) {
        this.touches = value;
        return self();
    }

    public final T prevCodeSmells(int value) {
        this.prevCodeSmells = value;
        return self();
    }

    public final T prevBuggy(boolean value) {
        this.prevBuggy = value;
        return self();
    }

    public final T startLine(int value) {
        this.startLine = value;
        return self();
    }

    public final T endLine(int value) {
        this.endLine = value;
        return self();
    }

    final T copyCommonFrom(MetricDatasetRowData data) {
        return projectName(data.projectName())
                .path(data.path())
                .releaseId(data.releaseId())
                .metrics(data.metrics())
                .commitHashes(data.commitHashes())
                .buggy(data.buggy())
                .codeSmells(data.codeSmells())
                .touches(data.touches())
                .prevCodeSmells(data.prevCodeSmells())
                .prevBuggy(data.prevBuggy())
                .startLine(data.startLine())
                .endLine(data.endLine());
    }

    final MetricDatasetRowData buildCommon() {
        validateCommon();
        return new MetricDatasetRowData(
                projectName,
                path,
                releaseId,
                metrics,
                commitHashes,
                buggy,
                codeSmells,
                touches,
                prevCodeSmells,
                prevBuggy,
                startLine,
                endLine);
    }

    final void validateCommon() {
        Objects.requireNonNull(projectName, "projectName missing");
        Objects.requireNonNull(path, "path missing");
        Objects.requireNonNull(releaseId, "releaseId missing");
        Objects.requireNonNull(metrics, "metrics missing");
        Objects.requireNonNull(commitHashes, "commitHashes missing");
    }

    protected abstract T self();
}
