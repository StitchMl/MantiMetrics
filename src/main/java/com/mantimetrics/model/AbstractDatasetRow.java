package com.mantimetrics.model;

import com.mantimetrics.metrics.MethodMetrics;

import java.util.List;

/**
 * Abstract base that carries the shared {@link MetricDatasetRowData} payload and exposes all
 * common getter delegates, eliminating the repetition between {@link ClassData} and
 * {@link MethodData}.
 *
 * <p>Subclasses only need to implement the entity-specific accessors
 * ({@link DatasetRow#getUniqueKey()}, {@link DatasetRow#toCsvLine()}) and the
 * entity-identifying field (class name / method signature).
 */
abstract class AbstractDatasetRow implements DatasetRow {

    /**
     * Shared immutable payload.
     * Accessible to subclasses for entity-specific operations (e.g. {@code getUniqueKey}).
     */
    protected final MetricDatasetRowData data;

    /**
     * Initialises the shared payload.
     *
     * @param data immutable shared dataset payload (non-null, already validated by the builder)
     */
    protected AbstractDatasetRow(MetricDatasetRowData data) {
        this.data = data;
    }

    /**
     * Returns the analyzed project name.
     *
     * @return project name
     */
    public String getProjectName() { return data.projectName(); }

    /**
     * {@inheritDoc}
     */
    @Override public String getPath() { return data.path(); }

    /**
     * Returns the release identifier that produced this row.
     *
     * @return release identifier
     */
    public String getReleaseId() { return data.releaseId(); }

    /**
     * Returns the static metrics computed for the entity.
     *
     * @return entity metrics
     */
    public MethodMetrics getMetrics() { return data.metrics(); }

    /**
     * Returns the commits touching the entity in the current release range.
     *
     * @return touching commit hashes
     */
    public List<String> getCommitHashes() { return data.commitHashes(); }

    /**
     * {@inheritDoc}
     */
    @Override public boolean isBuggy() { return data.buggy(); }

    /**
     * {@inheritDoc}
     */
    @Override public int getCodeSmells() { return data.codeSmells(); }

    /**
     * {@inheritDoc}
     */
    @Override public int getNSmells() { return data.nSmells(); }

    /**
     * Returns the number of touches in the current release.
     *
     * @return release-local touch count
     */
    public int getTouches() { return data.touches(); }

    /**
     * Returns the cumulative touch count across releases.
     *
     * @return cumulative touch count
     */
    public int getTotalTouches() { return data.totalTouches(); }

    /**
     * Returns the issue-linked touches in the current release.
     *
     * @return release-local issue touch count
     */
    public int getIssueTouches() { return data.issueTouches(); }

    /**
     * Returns the cumulative issue-linked touch count.
     *
     * @return cumulative issue touch count
     */
    public int getTotalIssueTouches() { return data.totalIssueTouches(); }

    /**
     * Returns the distinct authors touching the entity in the current release.
     *
     * @return release-local author count
     */
    public int getAuthors() { return data.authors(); }

    /**
     * Returns the cumulative distinct author count.
     *
     * @return cumulative author count
     */
    public int getTotalAuthors() { return data.totalAuthors(); }

    /**
     * Returns the lines added in the current release.
     *
     * @return added lines
     */
    public int getAddedLines() { return data.addedLines(); }

    /**
     * Returns the lines deleted in the current release.
     *
     * @return deleted lines
     */
    public int getDeletedLines() { return data.deletedLines(); }

    /**
     * Returns the current-release churn.
     *
     * @return churn value
     */
    public int getChurn() { return data.churn(); }

    /**
     * Returns the cumulative churn across releases.
     *
     * @return cumulative churn
     */
    public int getTotalChurn() { return data.totalChurn(); }

    /**
     * Returns the code smell count observed in the previous release.
     *
     * @return previous-release code smell count
     */
    public int getPrevCodeSmells() { return data.prevCodeSmells(); }

    /**
     * Reports whether the entity was buggy in the previous release.
     *
     * @return previous-release buggy flag
     */
    public boolean isPrevBuggy() { return data.prevBuggy(); }

    /**
     * Returns the number of analyzed releases in which the entity has existed.
     *
     * @return age in releases
     */
    public int getAgeInReleases() { return data.ageInReleases(); }

    /**
     * {@inheritDoc}
     */
    @Override public int getStartLine() { return data.startLine(); }

    /**
     * {@inheritDoc}
     */
    @Override public int getEndLine() { return data.endLine(); }
}
