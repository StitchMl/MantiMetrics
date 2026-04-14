package com.mantimetrics.model;

import com.mantimetrics.metrics.MethodMetrics;

import java.util.List;
import java.util.Objects;

@SuppressWarnings("unused")
public final class ClassData implements DatasetRow {
    private final MetricDatasetRowData data;
    private final String className;

    private ClassData(Builder builder) {
        this.data = builder.buildCommon();
        this.className = Objects.requireNonNull(builder.className, "className");
    }

    public String getProjectName() { return data.projectName(); }
    @Override public String getPath() { return data.path(); }
    public String getClassName() { return className; }
    public String getReleaseId() { return data.releaseId(); }
    public MethodMetrics getMetrics() { return data.metrics(); }
    public List<String> getCommitHashes() { return data.commitHashes(); }
    @Override public boolean isBuggy() { return data.buggy(); }
    @Override public int getCodeSmells() { return data.codeSmells(); }
    @Override public int getNSmells() { return data.nSmells(); }
    public int getTouches() { return data.touches(); }
    public int getTotalTouches() { return data.totalTouches(); }
    public int getIssueTouches() { return data.issueTouches(); }
    public int getTotalIssueTouches() { return data.totalIssueTouches(); }
    public int getAuthors() { return data.authors(); }
    public int getTotalAuthors() { return data.totalAuthors(); }
    public int getAddedLines() { return data.addedLines(); }
    public int getDeletedLines() { return data.deletedLines(); }
    public int getChurn() { return data.churn(); }
    public int getTotalChurn() { return data.totalChurn(); }
    public int getPrevCodeSmells() { return data.prevCodeSmells(); }
    public boolean isPrevBuggy() { return data.prevBuggy(); }
    public int getAgeInReleases() { return data.ageInReleases(); }
    @Override public int getStartLine() { return data.startLine(); }
    @Override public int getEndLine() { return data.endLine(); }
    @Override public String getUniqueKey() { return data.path() + "#" + className; }

    @Override
    public String toCsvLine() {
        return DatasetCsvFormatter.format(data, className);
    }

    public Builder toBuilder() {
        return new Builder()
                .copyCommonFrom(data)
                .className(className);
    }

    public static final class Builder extends MetricDatasetRowBuilder<Builder> {
        private String className;

        public Builder className(String value) {
            this.className = Objects.requireNonNull(value, "className");
            return this;
        }

        public ClassData build() {
            validateCommon();
            Objects.requireNonNull(className, "className missing");
            return new ClassData(this);
        }

        @Override
        protected Builder self() {
            return this;
        }
    }
}
