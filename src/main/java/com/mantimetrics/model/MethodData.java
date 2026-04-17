package com.mantimetrics.model;

import com.mantimetrics.metrics.MethodMetrics;

import java.util.List;
import java.util.Objects;

/**
 * Immutable method-level dataset row.
 */
@SuppressWarnings("unused")
public final class MethodData implements DatasetRow {
    private final MetricDatasetRowData data;
    private final String methodSignature;

    /**
     * Builds an immutable method row from its builder.
     *
     * @param builder builder containing the method-row state
     */
    private MethodData(Builder builder) {
        this.data = builder.buildCommon();
        this.methodSignature = Objects.requireNonNull(builder.methodSignature, "methodSignature");
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
     * Returns the method signature used as entity identifier.
     *
     * @return method signature
     */
    public String getMethodSignature() { return methodSignature; }

    /**
     * Returns the release identifier that produced this row.
     *
     * @return release identifier
     */
    public String getReleaseId() { return data.releaseId(); }

    /**
     * Returns the static metrics computed for the method.
     *
     * @return method metrics
     */
    public MethodMetrics getMetrics() { return data.metrics(); }

    /**
     * Returns the commits touching the method in the current release range.
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
     * {@inheritDoc}
     */
    @Override public String getUniqueKey() { return data.path() + "#" + methodSignature; }

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
     * Returns the distinct authors touching the method in the current release.
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
     * Reports whether the method was buggy in the previous release.
     *
     * @return previous-release buggy flag
     */
    public boolean isPrevBuggy() { return data.prevBuggy(); }

    /**
     * Returns the number of analyzed releases in which the method exists.
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

    /**
     * {@inheritDoc}
     */
    @Override
    public String toCsvLine() {
        return DatasetCsvFormatter.format(data, methodSignature);
    }

    /**
     * Compares method rows using their serialized dataset content.
     *
     * @param other object to compare
     * @return {@code true} when both rows carry the same relevant data
     */
    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof MethodData that)) return false;
        return isBuggy() == that.isBuggy()
                && getProjectName().equals(that.getProjectName())
                && getPath().equals(that.getPath())
                && methodSignature.equals(that.methodSignature)
                && getReleaseId().equals(that.getReleaseId())
                && getMetrics().equals(that.getMetrics())
                && getCommitHashes().equals(that.getCommitHashes());
    }

    /**
     * Returns a hash code consistent with {@link #equals(Object)}.
     *
     * @return row hash code
     */
    @Override
    public int hashCode() {
        return Objects.hash(
                getProjectName(),
                getPath(),
                methodSignature,
                getReleaseId(),
                getMetrics(),
                getCommitHashes(),
                isBuggy());
    }

    /**
     * Returns a concise debug representation of the method row.
     *
     * @return debug representation
     */
    @Override
    public String toString() {
        return "MethodData[" + getProjectName() + "/" + getPath() + "@"
                + getReleaseId() + ", signature=" + methodSignature + "]";
    }

    /**
     * Creates a builder pre-populated with the current row values.
     *
     * @return builder initialized from the current row
     */
    public Builder toBuilder() {
        return new Builder()
                .copyCommonFrom(data)
                .methodSignature(methodSignature);
    }

    /**
     * Builder for immutable {@link MethodData} instances.
     */
    public static final class Builder extends MetricDatasetRowBuilder<Builder> {
        private String methodSignature;

        /**
         * Sets the method signature for the row being built.
         *
         * @param value method signature
         * @return current builder
         */
        public Builder methodSignature(String value) {
            this.methodSignature = Objects.requireNonNull(value, "methodSignature");
            return this;
        }

        /**
         * Builds the immutable method row.
         *
         * @return immutable method row
         */
        public MethodData build() {
            validateCommon();
            Objects.requireNonNull(methodSignature, "methodSignature missing");
            return new MethodData(this);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        protected Builder self() {
            return this;
        }
    }
}
