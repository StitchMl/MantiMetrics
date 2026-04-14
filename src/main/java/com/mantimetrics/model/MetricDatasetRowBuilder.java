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
    protected int totalTouches;
    protected int issueTouches;
    protected int totalIssueTouches;
    protected int authors;
    protected int totalAuthors;
    protected int addedLines;
    protected int deletedLines;
    protected int churn;
    protected int totalChurn;
    protected int prevCodeSmells;
    protected boolean prevBuggy;
    protected int ageInReleases;
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

    public final T totalTouches(int value) {
        this.totalTouches = value;
        return self();
    }

    public final T issueTouches(int value) {
        this.issueTouches = value;
        return self();
    }

    public final T totalIssueTouches(int value) {
        this.totalIssueTouches = value;
        return self();
    }

    public final T authors(int value) {
        this.authors = value;
        return self();
    }

    public final T totalAuthors(int value) {
        this.totalAuthors = value;
        return self();
    }

    public final T addedLines(int value) {
        this.addedLines = value;
        return self();
    }

    public final T deletedLines(int value) {
        this.deletedLines = value;
        return self();
    }

    public final T churn(int value) {
        this.churn = value;
        return self();
    }

    public final T totalChurn(int value) {
        this.totalChurn = value;
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

    public final T ageInReleases(int value) {
        this.ageInReleases = value;
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
                .totalTouches(data.totalTouches())
                .issueTouches(data.issueTouches())
                .totalIssueTouches(data.totalIssueTouches())
                .authors(data.authors())
                .totalAuthors(data.totalAuthors())
                .addedLines(data.addedLines())
                .deletedLines(data.deletedLines())
                .churn(data.churn())
                .totalChurn(data.totalChurn())
                .prevCodeSmells(data.prevCodeSmells())
                .prevBuggy(data.prevBuggy())
                .ageInReleases(data.ageInReleases())
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
                totalTouches,
                issueTouches,
                totalIssueTouches,
                authors,
                totalAuthors,
                addedLines,
                deletedLines,
                churn,
                totalChurn,
                prevCodeSmells,
                prevBuggy,
                ageInReleases,
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
