package com.mantimetrics.datasetsetting;

import com.mantimetrics.feature.ClassMetrics;

import java.util.List;

/**
 * Abstract base carrying the shared {@link DatasetRowData} payload and exposing the common getters.
 */
@SuppressWarnings("unused")
abstract class AbstractDatasetRow implements DatasetRow {

    /** Shared immutable payload. */
    protected final DatasetRowData data;

    /**
     * Initializes the shared payload.
     *
     * @param data immutable shared dataset payload
     */
    protected AbstractDatasetRow(DatasetRowData data) {
        this.data = data;
    }

    /** @return project name */
    public String getProjectName() { return data.projectName(); }

    /** {@inheritDoc} */
    @Override public String getPath() { return data.path(); }

    /** @return release identifier */
    public String getReleaseId() { return data.releaseId(); }

    /** @return class metrics */
    public ClassMetrics getMetrics() { return data.metrics(); }

    /** @return touching commit hashes */
    public List<String> getCommitHashes() { return data.commitHashes(); }

    /** {@inheritDoc} */
    @Override public boolean isBuggy() { return data.buggy(); }

    /** {@inheritDoc} */
    @Override public int getCodeSmells() { return data.codeSmells(); }

    /** {@inheritDoc} */
    @Override public int getNSmells() { return data.nSmells(); }

    /** @return release-local touch count */
    public int getTouches() { return data.touches(); }

    /** @return cumulative touch count */
    public int getTotalTouches() { return data.totalTouches(); }

    /** @return release-local issue touch count */
    public int getIssueTouches() { return data.issueTouches(); }

    /** @return cumulative issue touch count */
    public int getTotalIssueTouches() { return data.totalIssueTouches(); }

    /** @return release-local author count */
    public int getAuthors() { return data.authors(); }

    /** @return cumulative author count */
    public int getTotalAuthors() { return data.totalAuthors(); }

    /** @return added lines */
    public int getAddedLines() { return data.addedLines(); }

    /** @return deleted lines */
    public int getDeletedLines() { return data.deletedLines(); }

    /** @return churn value */
    public int getChurn() { return data.churn(); }

    /** @return cumulative churn */
    public int getTotalChurn() { return data.totalChurn(); }

    /** @return previous-release NSmells */
    public int getPrevCodeSmells() { return data.prevCodeSmells(); }

    /** @return previous-release buggy flag */
    public boolean isPrevBuggy() { return data.prevBuggy(); }

    /** @return age in releases */
    public int getAgeInReleases() { return data.ageInReleases(); }

    /** {@inheritDoc} */
    @Override public int getStartLine() { return data.startLine(); }

    /** {@inheritDoc} */
    @Override public int getEndLine() { return data.endLine(); }
}
