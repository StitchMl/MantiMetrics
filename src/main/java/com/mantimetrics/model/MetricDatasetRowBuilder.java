package com.mantimetrics.model;

import com.mantimetrics.metrics.MethodMetrics;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Base builder shared by class-level and method-level dataset rows.
 *
 * @param <T> concrete builder type for fluent chaining
 */
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
    protected int maxLoc;
    protected int maxCyclomatic;
    protected int maxCognitive;
    protected int maxNSmells;
    protected int maxStmtCount;
    protected int maxDistinctOperators;
    protected int maxDistinctOperands;
    protected int maxTotalOperators;
    protected int maxTotalOperands;
    protected double maxVocabulary;
    protected double maxLength;
    protected double maxVolume;
    protected double maxDifficulty;
    protected double maxEffort;
    protected int maxNestingDepth;
    protected boolean everLongMethod;
    protected boolean everGodClass;
    protected boolean everFeatureEnvy;
    protected boolean everDuplicatedCode;
    protected int maxCodeSmells;
    protected double maxSmellDensity;

    /**
     * Sets the analyzed project name.
     *
     * @param value project name
     * @return current builder
     */
    public final T projectName(String value) {
        this.projectName = Objects.requireNonNull(value, "projectName");
        return self();
    }

    /**
     * Sets the normalized relative source path.
     *
     * @param value normalized relative source path
     * @return current builder
     */
    public final T path(String value) {
        this.path = Objects.requireNonNull(value, "path");
        return self();
    }

    /**
     * Sets the release identifier.
     *
     * @param value release identifier
     * @return current builder
     */
    public final T releaseId(String value) {
        this.releaseId = Objects.requireNonNull(value, "releaseId");
        return self();
    }

    /**
     * Sets the static metrics computed for the entity.
     *
     * @param value method or class metrics
     * @return current builder
     */
    public final T metrics(MethodMetrics value) {
        this.metrics = Objects.requireNonNull(value, "metrics");
        return self();
    }

    /**
     * Sets the commit hashes touching the entity in the current release.
     *
     * @param value commit hashes
     * @return current builder
     */
    public final T commitHashes(List<String> value) {
        this.commitHashes = List.copyOf(Objects.requireNonNull(value, "commitHashes"));
        return self();
    }

    /**
     * Sets the buggy label for the current release.
     *
     * @param value buggy flag
     * @return current builder
     */
    public final T buggy(boolean value) {
        this.buggy = value;
        return self();
    }

    /**
     * Sets the PMD code smell count.
     *
     * @param value PMD code smell count
     * @return current builder
     */
    public final T codeSmells(int value) {
        this.codeSmells = value;
        return self();
    }

    /**
     * Sets the number of touches in the current release.
     *
     * @param value release-local touch count
     * @return current builder
     */
    public final T touches(int value) {
        this.touches = value;
        return self();
    }

    /**
     * Sets the cumulative touch count.
     *
     * @param value cumulative touch count
     * @return current builder
     */
    public final T totalTouches(int value) {
        this.totalTouches = value;
        return self();
    }

    /**
     * Sets the issue-linked touches in the current release.
     *
     * @param value release-local issue touch count
     * @return current builder
     */
    public final T issueTouches(int value) {
        this.issueTouches = value;
        return self();
    }

    /**
     * Sets the cumulative issue-linked touch count.
     *
     * @param value cumulative issue touch count
     * @return current builder
     */
    public final T totalIssueTouches(int value) {
        this.totalIssueTouches = value;
        return self();
    }

    /**
     * Sets the distinct authors in the current release.
     *
     * @param value release-local author count
     * @return current builder
     */
    public final T authors(int value) {
        this.authors = value;
        return self();
    }

    /**
     * Sets the cumulative distinct author count.
     *
     * @param value cumulative author count
     * @return current builder
     */
    public final T totalAuthors(int value) {
        this.totalAuthors = value;
        return self();
    }

    /**
     * Sets the lines added in the current release.
     *
     * @param value added lines
     * @return current builder
     */
    public final T addedLines(int value) {
        this.addedLines = value;
        return self();
    }

    /**
     * Sets the lines deleted in the current release.
     *
     * @param value deleted lines
     * @return current builder
     */
    public final T deletedLines(int value) {
        this.deletedLines = value;
        return self();
    }

    /**
     * Sets the current-release churn.
     *
     * @param value churn value
     * @return current builder
     */
    public final T churn(int value) {
        this.churn = value;
        return self();
    }

    /**
     * Sets the cumulative churn.
     *
     * @param value cumulative churn
     * @return current builder
     */
    public final T totalChurn(int value) {
        this.totalChurn = value;
        return self();
    }

    /**
     * Sets the previous-release code smell count.
     *
     * @param value previous code smell count
     * @return current builder
     */
    public final T prevCodeSmells(int value) {
        this.prevCodeSmells = value;
        return self();
    }

    /**
     * Sets the previous-release buggy label.
     *
     * @param value previous buggy flag
     * @return current builder
     */
    public final T prevBuggy(boolean value) {
        this.prevBuggy = value;
        return self();
    }

    /**
     * Sets the number of releases in which the entity has existed.
     *
     * @param value age in releases
     * @return current builder
     */
    public final T ageInReleases(int value) {
        this.ageInReleases = value;
        return self();
    }

    /**
     * Sets the inclusive start line of the entity.
     *
     * @param value start line
     * @return current builder
     */
    public final T startLine(int value) {
        this.startLine = value;
        return self();
    }

    /**
     * Sets the inclusive end line of the entity.
     *
     * @param value end line
     * @return current builder
     */
    public final T endLine(int value) {
        this.endLine = value;
        return self();
    }

    /**
     * Copies all shared fields from an existing immutable payload.
     *
     * @param data immutable shared dataset payload
     * @return current builder
     */
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
                .endLine(data.endLine())
                .maxLoc(data.maxLoc())
                .maxCyclomatic(data.maxCyclomatic())
                .maxCognitive(data.maxCognitive())
                .maxNSmells(data.maxNSmells())
                .maxStmtCount(data.maxStmtCount())
                .maxDistinctOperators(data.maxDistinctOperators())
                .maxDistinctOperands(data.maxDistinctOperands())
                .maxTotalOperators(data.maxTotalOperators())
                .maxTotalOperands(data.maxTotalOperands())
                .maxVocabulary(data.maxVocabulary())
                .maxLength(data.maxLength())
                .maxVolume(data.maxVolume())
                .maxDifficulty(data.maxDifficulty())
                .maxEffort(data.maxEffort())
                .maxNestingDepth(data.maxNestingDepth())
                .everLongMethod(data.everLongMethod())
                .everGodClass(data.everGodClass())
                .everFeatureEnvy(data.everFeatureEnvy())
                .everDuplicatedCode(data.everDuplicatedCode())
                .maxCodeSmells(data.maxCodeSmells())
                .maxSmellDensity(data.maxSmellDensity());
    }

    /**
     * Builds the immutable shared payload after validating mandatory fields.
     *
     * @return immutable shared dataset payload
     */
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
                endLine,
                maxLoc,
                maxCyclomatic,
                maxCognitive,
                maxNSmells,
                maxStmtCount,
                maxDistinctOperators,
                maxDistinctOperands,
                maxTotalOperators,
                maxTotalOperands,
                maxVocabulary,
                maxLength,
                maxVolume,
                maxDifficulty,
                maxEffort,
                maxNestingDepth,
                everLongMethod,
                everGodClass,
                everFeatureEnvy,
                everDuplicatedCode,
                maxCodeSmells,
                maxSmellDensity);
    }

    /**
     * Verifies that the mandatory shared fields were provided.
     */
    final void validateCommon() {
        Objects.requireNonNull(projectName, "projectName missing");
        Objects.requireNonNull(path, "path missing");
        Objects.requireNonNull(releaseId, "releaseId missing");
        Objects.requireNonNull(metrics, "metrics missing");
        Objects.requireNonNull(commitHashes, "commitHashes missing");
    }

    /**
     * Sets the maximum LOC seen across all releases (asterisk feature).
     *
     * @param value max LOC
     * @return current builder
     */
    public final T maxLoc(int value) {
        this.maxLoc = value;
        return self();
    }

    /**
     * Sets the maximum cyclomatic complexity seen across all releases (asterisk feature).
     *
     * @param value max cyclomatic
     * @return current builder
     */
    public final T maxCyclomatic(int value) {
        this.maxCyclomatic = value;
        return self();
    }

    /**
     * Sets the maximum cognitive complexity seen across all releases (asterisk feature).
     *
     * @param value max cognitive
     * @return current builder
     */
    public final T maxCognitive(int value) {
        this.maxCognitive = value;
        return self();
    }

    /**
     * Sets the maximum total smell count seen across all releases (asterisk feature).
     *
     * @param value max nSmells
     * @return current builder
     */
    public final T maxNSmells(int value) {
        this.maxNSmells = value;
        return self();
    }

    /** @return current builder */
    public final T maxStmtCount(int value) { this.maxStmtCount = value; return self(); }
    /** @return current builder */
    public final T maxDistinctOperators(int value) { this.maxDistinctOperators = value; return self(); }
    /** @return current builder */
    public final T maxDistinctOperands(int value) { this.maxDistinctOperands = value; return self(); }
    /** @return current builder */
    public final T maxTotalOperators(int value) { this.maxTotalOperators = value; return self(); }
    /** @return current builder */
    public final T maxTotalOperands(int value) { this.maxTotalOperands = value; return self(); }
    /** @return current builder */
    public final T maxVocabulary(double value) { this.maxVocabulary = value; return self(); }
    /** @return current builder */
    public final T maxLength(double value) { this.maxLength = value; return self(); }
    /** @return current builder */
    public final T maxVolume(double value) { this.maxVolume = value; return self(); }
    /** @return current builder */
    public final T maxDifficulty(double value) { this.maxDifficulty = value; return self(); }
    /** @return current builder */
    public final T maxEffort(double value) { this.maxEffort = value; return self(); }
    /** @return current builder */
    public final T maxNestingDepth(int value) { this.maxNestingDepth = value; return self(); }
    /** @return current builder */
    public final T everLongMethod(boolean value) { this.everLongMethod = value; return self(); }
    /** @return current builder */
    public final T everGodClass(boolean value) { this.everGodClass = value; return self(); }
    /** @return current builder */
    public final T everFeatureEnvy(boolean value) { this.everFeatureEnvy = value; return self(); }
    /** @return current builder */
    public final T everDuplicatedCode(boolean value) { this.everDuplicatedCode = value; return self(); }
    /** @return current builder */
    public final T maxCodeSmells(int value) { this.maxCodeSmells = value; return self(); }
    /** @return current builder */
    public final T maxSmellDensity(double value) { this.maxSmellDensity = value; return self(); }

    /**
     * Returns the concrete builder instance for fluent chaining.
     *
     * @return concrete builder
     */
    protected abstract T self();
}
