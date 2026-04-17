package com.mantimetrics.model;

import com.mantimetrics.metrics.MethodMetrics;

import java.util.List;
import java.util.Objects;

/**
 * Immutable class-level dataset row.
 */
@SuppressWarnings("unused")
public final class ClassData implements DatasetRow {
    private final MetricDatasetRowData data;
    private final String className;

    /**
     * Builds an immutable class row from its builder.
     *
     * @param builder builder containing the class-row state
     */
    private ClassData(Builder builder) {
        this.data = builder.buildCommon();
        this.className = Objects.requireNonNull(builder.className, "className");
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
     * Returns the fully qualified or display name of the class entity.
     *
     * @return class name
     */
    public String getClassName() { return className; }

    /**
     * Returns the release identifier that produced this row.
     *
     * @return release identifier
     */
    public String getReleaseId() { return data.releaseId(); }

    /**
     * Returns the static metrics computed for the class.
     *
     * @return class metrics
     */
    public MethodMetrics getMetrics() { return data.metrics(); }

    /**
     * Returns the commits touching the class in the current release range.
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
     * Returns the distinct authors touching the class in the current release.
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
     * Reports whether the class was buggy in the previous release.
     *
     * @return previous-release buggy flag
     */
    public boolean isPrevBuggy() { return data.prevBuggy(); }

    /**
     * Returns the number of analyzed releases in which the class exists.
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
    @Override public String getUniqueKey() { return data.path() + "#" + className; }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toCsvLine() {
        return DatasetCsvFormatter.format(data, className);
    }

    /**
     * Creates a builder pre-populated with the current row values.
     *
     * @return builder initialized from the current row
     */
    public Builder toBuilder() {
        return new Builder()
                .copyCommonFrom(data)
                .className(className);
    }

    /**
     * Builder for immutable {@link ClassData} instances.
     */
    public static final class Builder extends MetricDatasetRowBuilder<Builder> {
        private String className;

        /**
         * Sets the class name for the row being built.
         *
         * @param value class name
         * @return current builder
         */
        public Builder className(String value) {
            this.className = Objects.requireNonNull(value, "className");
            return this;
        }

        /**
         * Builds the immutable class row.
         *
         * @return immutable class row
         */
        public ClassData build() {
            validateCommon();
            Objects.requireNonNull(className, "className missing");
            return new ClassData(this);
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
