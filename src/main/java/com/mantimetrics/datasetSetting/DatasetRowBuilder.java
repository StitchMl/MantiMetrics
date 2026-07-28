package com.mantimetrics.datasetSetting;

import com.mantimetrics.feature.ClassMetrics;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Base builder for class-level dataset rows (Milestone 1 lean schema).
 *
 * @param <T> concrete builder type for fluent chaining
 */
abstract class DatasetRowBuilder<T extends DatasetRowBuilder<T>> {
    protected String projectName;
    protected String path;
    protected String releaseId;
    protected ClassMetrics metrics;
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
    protected int maxLoc;
    protected int maxWmc;
    protected int maxNSmells;
    protected int priorityMax;
    protected double priorityAvg;
    protected int typeRiskMax;
    protected double typeRiskAvg;
    protected int componentCountMax;
    protected double componentCountAvg;
    protected int openTickets;
    protected double tlccLin;
    protected double tlccLog;

    /** @param value project name @return current builder */
    public final T projectName(String value) { this.projectName = Objects.requireNonNull(value, "projectName"); return self(); }
    /** @param value normalized relative source path @return current builder */
    public final T path(String value) { this.path = Objects.requireNonNull(value, "path"); return self(); }
    /** @param value release identifier @return current builder */
    public final T releaseId(String value) { this.releaseId = Objects.requireNonNull(value, "releaseId"); return self(); }
    /** @param value class metrics @return current builder */
    public final T metrics(ClassMetrics value) { this.metrics = Objects.requireNonNull(value, "metrics"); return self(); }
    /** @param value commit hashes @return current builder */
    public final T commitHashes(List<String> value) { this.commitHashes = List.copyOf(Objects.requireNonNull(value, "commitHashes")); return self(); }
    /** @param value buggy flag @return current builder */
    public final T buggy(boolean value) { this.buggy = value; return self(); }
    /** @param value NSmells count @return current builder */
    public final T codeSmells(int value) { this.codeSmells = value; return self(); }
    /** @param value release-local touch count @return current builder */
    public final T touches(int value) { this.touches = value; return self(); }
    /** @param value cumulative touch count @return current builder */
    public final T totalTouches(int value) { this.totalTouches = value; return self(); }
    /** @param value release-local issue touch count @return current builder */
    public final T issueTouches(int value) { this.issueTouches = value; return self(); }
    /** @param value cumulative issue touch count @return current builder */
    public final T totalIssueTouches(int value) { this.totalIssueTouches = value; return self(); }
    /** @param value release-local author count @return current builder */
    public final T authors(int value) { this.authors = value; return self(); }
    /** @param value cumulative author count @return current builder */
    public final T totalAuthors(int value) { this.totalAuthors = value; return self(); }
    /** @param value added lines @return current builder */
    public final T addedLines(int value) { this.addedLines = value; return self(); }
    /** @param value deleted lines @return current builder */
    public final T deletedLines(int value) { this.deletedLines = value; return self(); }
    /** @param value churn value @return current builder */
    public final T churn(int value) { this.churn = value; return self(); }
    /** @param value cumulative churn @return current builder */
    public final T totalChurn(int value) { this.totalChurn = value; return self(); }
    /** @param value previous NSmells @return current builder */
    public final T prevCodeSmells(int value) { this.prevCodeSmells = value; return self(); }
    /** @param value previous buggy flag @return current builder */
    public final T prevBuggy(boolean value) { this.prevBuggy = value; return self(); }
    /** @param value age in releases @return current builder */
    public final T ageInReleases(int value) { this.ageInReleases = value; return self(); }
    /** @param value start line @return current builder */
    public final T startLine(int value) { this.startLine = value; return self(); }
    /** @param value end line @return current builder */
    public final T endLine(int value) { this.endLine = value; return self(); }
    /** @param value max LOC @return current builder */
    public final T maxLoc(int value) { this.maxLoc = value; return self(); }
    /** @param value max WMC @return current builder */
    public final T maxWmc(int value) { this.maxWmc = value; return self(); }
    /** @param value max NSmells @return current builder */
    public final T maxNSmells(int value) { this.maxNSmells = value; return self(); }
    /** @param value max priority @return current builder */
    public final T priorityMax(int value) { this.priorityMax = value; return self(); }
    /** @param value mean priority @return current builder */
    public final T priorityAvg(double value) { this.priorityAvg = value; return self(); }
    /** @param value max type-risk @return current builder */
    public final T typeRiskMax(int value) { this.typeRiskMax = value; return self(); }
    /** @param value mean type-risk @return current builder */
    public final T typeRiskAvg(double value) { this.typeRiskAvg = value; return self(); }
    /** @param value max component count @return current builder */
    public final T componentCountMax(int value) { this.componentCountMax = value; return self(); }
    /** @param value mean component count @return current builder */
    public final T componentCountAvg(double value) { this.componentCountAvg = value; return self(); }
    /** @param value open tickets @return current builder */
    public final T openTickets(int value) { this.openTickets = value; return self(); }
    /** @param value TLCC linear @return current builder */
    public final T tlccLin(double value) { this.tlccLin = value; return self(); }
    /** @param value TLCC log @return current builder */
    public final T tlccLog(double value) { this.tlccLog = value; return self(); }

    /**
     * Copies all shared fields from an existing immutable payload.
     *
     * @param data immutable shared dataset payload
     * @return current builder
     */
    final T copyCommonFrom(DatasetRowData data) {
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
                .endLine(data.endLine())
                .maxLoc(data.maxLoc())
                .maxWmc(data.maxWmc())
                .maxNSmells(data.maxNSmells())
                .priorityMax(data.priorityMax())
                .priorityAvg(data.priorityAvg())
                .typeRiskMax(data.typeRiskMax())
                .typeRiskAvg(data.typeRiskAvg())
                .componentCountMax(data.componentCountMax())
                .componentCountAvg(data.componentCountAvg())
                .openTickets(data.openTickets())
                .tlccLin(data.tlccLin())
                .tlccLog(data.tlccLog());
    }

    /**
     * Builds the immutable shared payload after validating mandatory fields.
     *
     * @return immutable shared dataset payload
     */
    final DatasetRowData buildCommon() {
        validateCommon();
        return new DatasetRowData(
                projectName, path, releaseId, metrics, commitHashes, buggy, codeSmells,
                touches, totalTouches, issueTouches, totalIssueTouches, authors, totalAuthors,
                addedLines, deletedLines, churn, totalChurn, prevCodeSmells, prevBuggy,
                ageInReleases, startLine, endLine, maxLoc, maxWmc, maxNSmells,
                priorityMax, priorityAvg, typeRiskMax, typeRiskAvg,
                componentCountMax, componentCountAvg, openTickets,
                tlccLin, tlccLog);
    }

    /** Verifies that the mandatory shared fields were provided. */
    final void validateCommon() {
        Objects.requireNonNull(projectName, "projectName missing");
        Objects.requireNonNull(path, "path missing");
        Objects.requireNonNull(releaseId, "releaseId missing");
        Objects.requireNonNull(metrics, "metrics missing");
        Objects.requireNonNull(commitHashes, "commitHashes missing");
    }

    /**
     * Returns the concrete builder instance for fluent chaining.
     *
     * @return concrete builder
     */
    protected abstract T self();
}
