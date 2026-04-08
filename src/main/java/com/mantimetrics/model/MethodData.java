package com.mantimetrics.model;

import com.mantimetrics.metrics.MethodMetrics;

import java.util.List;
import java.util.Objects;

@SuppressWarnings("unused")
public final class MethodData implements DatasetRow {
    private final MetricDatasetRowData data;
    private final String methodSignature;

    private MethodData(Builder builder) {
        this.data = builder.buildCommon();
        this.methodSignature = Objects.requireNonNull(builder.methodSignature, "methodSignature");
    }

    public String getProjectName() { return data.projectName(); }
    @Override public String getPath() { return data.path(); }
    public String getMethodSignature() { return methodSignature; }
    public String getReleaseId() { return data.releaseId(); }
    public MethodMetrics getMetrics() { return data.metrics(); }
    public List<String> getCommitHashes() { return data.commitHashes(); }
    @Override public boolean isBuggy() { return data.buggy(); }
    @Override public int getCodeSmells() { return data.codeSmells(); }
    @Override public int getNSmells() { return data.nSmells(); }
    @Override public String getUniqueKey() { return data.path() + "#" + methodSignature; }
    public int getTouches() { return data.touches(); }
    public int getPrevCodeSmells() { return data.prevCodeSmells(); }
    public boolean isPrevBuggy() { return data.prevBuggy(); }
    @Override public int getStartLine() { return data.startLine(); }
    @Override public int getEndLine() { return data.endLine(); }

    @Override
    public String toCsvLine() {
        return DatasetCsvFormatter.format(data, methodSignature);
    }

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

    @Override
    public String toString() {
        return "MethodData[" + getProjectName() + "/" + getPath() + "@"
                + getReleaseId() + ", signature=" + methodSignature + "]";
    }

    public Builder toBuilder() {
        return new Builder()
                .copyCommonFrom(data)
                .methodSignature(methodSignature);
    }

    public static final class Builder extends MetricDatasetRowBuilder<Builder> {
        private String methodSignature;

        public Builder methodSignature(String value) {
            this.methodSignature = Objects.requireNonNull(value, "methodSignature");
            return this;
        }

        public MethodData build() {
            validateCommon();
            Objects.requireNonNull(methodSignature, "methodSignature missing");
            return new MethodData(this);
        }

        @Override
        protected Builder self() {
            return this;
        }
    }
}
