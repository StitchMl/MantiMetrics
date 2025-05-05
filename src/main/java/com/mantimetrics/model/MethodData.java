package com.mantimetrics.model;

import com.mantimetrics.metrics.MethodMetrics;
import java.util.List;

public class MethodData {
    private final String projectName;
    private final String path;
    private final String methodSignature;
    private final String releaseId;
    private final String versionId;
    private final String commitId;
    private final MethodMetrics metrics;
    private final List<String> commitHashes;
    private final boolean buggy;

    private MethodData(Builder b) {
        this.projectName     = b.projectName.toUpperCase();
        this.path            = b.path;
        this.methodSignature = b.methodSignature;
        this.releaseId       = b.releaseId;
        this.versionId       = b.versionId;
        this.commitId        = b.commitId;
        this.metrics         = b.metrics;
        this.commitHashes    = List.copyOf(b.commitHashes);
        this.buggy           = b.buggy;
    }

    // getters...
    public List<String> getCommitHashes() { return commitHashes; }
    public boolean isBuggy()              { return buggy; }

    public String toCsvLine() {
        String feats = String.join(",",
                String.valueOf(metrics.getLoc()),
                String.valueOf(metrics.getStmtCount()),
                String.valueOf(metrics.getCyclomatic()),
                String.valueOf(metrics.getCognitive()),
                String.valueOf(metrics.getDistinctOperators()),
                String.valueOf(metrics.getDistinctOperands()),
                String.valueOf(metrics.getTotalOperators()),
                String.valueOf(metrics.getTotalOperands()),
                String.valueOf(metrics.getVocabulary()),
                String.valueOf(metrics.getLength()),
                String.valueOf(metrics.getVolume()),
                String.valueOf(metrics.getDifficulty()),
                String.valueOf(metrics.getEffort()),
                String.valueOf(metrics.getMaxNestingDepth()),
                metrics.isLongMethod()     ? "1":"0",
                metrics.isGodClass()       ? "1":"0",
                metrics.isFeatureEnvy()    ? "1":"0",
                metrics.isDuplicatedCode() ? "1":"0"
        );

        return String.join(",",
                projectName,
                path,
                "\"" + methodSignature.replace("\"","\"\"") + "\"",
                releaseId,
                versionId,
                commitId,
                feats,
                buggy ? "yes" : "no"
        );
    }

    public Builder toBuilder() {
        return new Builder()
                .projectName(this.projectName)
                .path(this.path)
                .methodSignature(this.methodSignature)
                .releaseId(this.releaseId)
                .versionId(this.versionId)
                .commitId(this.commitId)
                .metrics(this.metrics)
                .commitHashes(this.commitHashes)
                .buggy(this.buggy);
    }

    public static class Builder {
        private String projectName;
        private String path;
        private String methodSignature;
        private String releaseId;
        private String versionId;
        private String commitId;
        private MethodMetrics metrics;
        private List<String> commitHashes;
        private boolean buggy;

        public Builder projectName(String projectName) {
            this.projectName = projectName; return this;
        }
        public Builder path(String path) {
            this.path = path; return this;
        }
        public Builder methodSignature(String sig) {
            this.methodSignature = sig; return this;
        }
        public Builder releaseId(String releaseId) {
            this.releaseId = releaseId; return this;
        }
        public Builder versionId(String versionId) {
            this.versionId = versionId; return this;
        }
        public Builder commitId(String commitId) {
            this.commitId = commitId; return this;
        }
        public Builder metrics(MethodMetrics metrics) {
            this.metrics = metrics; return this;
        }
        public Builder commitHashes(List<String> hashes) {
            this.commitHashes = List.copyOf(hashes); return this;
        }
        public Builder buggy(boolean buggy) {
            this.buggy = buggy; return this;
        }

        private void validate() {
            if (projectName == null || path == null || methodSignature == null
                    || releaseId == null || versionId == null || commitId == null
                    || metrics == null || commitHashes == null) {
                throw new IllegalStateException("Missing required fields in MethodData");
            }
        }

        public MethodData build() {
            validate();
            return new MethodData(this);
        }
    }
}