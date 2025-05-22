package com.mantimetrics.model;

import com.mantimetrics.metrics.MethodMetrics;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.StringJoiner;

public class MethodData {
    private final String projectName;
    private final String path;
    private final String methodSignature;
    private final String releaseId;
    private final MethodMetrics metrics;
    private final List<String> commitHashes;
    private final boolean buggy;
    private final int codeSmells;
    private final int touches;
    private final int prevCodeSmells;
    private final boolean prevBuggy;
    private final int startLine;
    private final int endLine;

    /**
     * Constructor for MethodData.
     * @param b Builder object containing all the required fields.
     */
    private MethodData(Builder b) {
        this.projectName     = b.projectName;
        this.path            = b.path;
        this.methodSignature = b.methodSignature;
        this.releaseId       = b.releaseId;
        this.metrics         = b.metrics;
        this.commitHashes    = List.copyOf(b.commitHashes);
        this.buggy           = b.buggy;
        this.codeSmells      = b.codeSmells;
        this.touches         = b.touches;
        this.prevCodeSmells  = b.prevCodeSmells;
        this.prevBuggy       = b.prevBuggy;
        this.startLine       = b.startLine;
        this.endLine         = b.endLine;
    }

    // Getter methods
    public String getProjectName()        { return projectName; }
    public String getPath()               { return path; }
    public String getMethodSignature()    { return methodSignature; }
    public String getReleaseId()          { return releaseId; }
    public MethodMetrics getMetrics()     { return metrics; }
    public List<String> getCommitHashes() { return commitHashes; }
    public boolean isBuggy()              { return buggy; }
    public int getCodeSmells()            { return codeSmells; }
    public String getUniqueKey()          { return path + "#" + methodSignature;}
    public int getTouches()               { return touches; }
    public int getPrevCodeSmells()        { return prevCodeSmells; }
    public boolean isPrevBuggy()          { return prevBuggy; }
    public int getStartLine()             { return startLine; }
    public int getEndLine()               { return endLine; }

    /**
     * Builds a CSV line with appropriate quoting and escaping.
     */
    public String toCsvLine() {
        StringJoiner sj = new StringJoiner(",");
        sj.add(projectName)
                .add(path)
                // escape embedded quotes per RFC4180
                .add('"' + methodSignature.replace("\"", "\"\"") + '"')
                .add(releaseId);

        // metrics fields
        sj.add(String.valueOf(metrics.getLoc()))
                .add(String.valueOf(metrics.getStmtCount()))
                .add(String.valueOf(metrics.getCyclomatic()))
                .add(String.valueOf(metrics.getCognitive()))
                .add(String.valueOf(metrics.getDistinctOperators()))
                .add(String.valueOf(metrics.getDistinctOperands()))
                .add(String.valueOf(metrics.getTotalOperators()))
                .add(String.valueOf(metrics.getTotalOperands()))
                .add(String.valueOf(metrics.getVocabulary()))
                .add(String.valueOf(metrics.getLength()))
                .add(String.valueOf(metrics.getVolume()))
                .add(String.valueOf(metrics.getDifficulty()))
                .add(String.valueOf(metrics.getEffort()))
                .add(String.valueOf(metrics.getMaxNestingDepth()))
                .add(metrics.isLongMethod()     ? "1" : "0")
                .add(metrics.isGodClass()       ? "1" : "0")
                .add(metrics.isFeatureEnvy()    ? "1" : "0")
                .add(metrics.isDuplicatedCode() ? "1" : "0")
                .add(String.valueOf(codeSmells))
                .add(String.valueOf(touches))
                .add(String.valueOf(prevCodeSmells))
                .add(prevBuggy ? "yes" : "no")
                .add(buggy ? "yes" : "no");
        return sj.toString();
    }

    /**
     * Standard equals() following the contract: reflexive, symmetric, transitive,
     * consistent, and null-safe. Must override hashCode() accordingly.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MethodData)) return false;
        MethodData that = (MethodData) o;
        return buggy == that.buggy &&
                projectName.equals(that.projectName) &&
                path.equals(that.path) &&
                methodSignature.equals(that.methodSignature) &&
                releaseId.equals(that.releaseId) &&
                metrics.equals(that.metrics) &&
                commitHashes.equals(that.commitHashes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(projectName, path, methodSignature,
                releaseId,
                metrics, commitHashes, buggy);
    }

    @Override
    public String toString() {
        return "MethodData[" + projectName + "/" + path +
                "@" + releaseId + ", signature=" + methodSignature + "]";
    }

    /** Returns a CSV header line with the same fields as toCsvLine() */
    public Builder toBuilder() {
        return new Builder()
                .projectName(this.projectName)
                .path(this.path)
                .methodSignature(this.methodSignature)
                .releaseId(this.releaseId)
                .metrics(this.metrics)
                .commitHashes(this.commitHashes)
                .buggy(this.buggy)
                .startLine(this.startLine)
                .endLine(this.endLine)
                .codeSmells(this.codeSmells);
    }

    /**
     * Builder for MethodData. Enforces required fields and immutability.
     */
    public static class Builder {
        private String projectName;
        private String path;
        private String methodSignature;
        private String releaseId;
        private MethodMetrics metrics;
        private List<String> commitHashes = Collections.emptyList();
        private boolean buggy;
        private int codeSmells;
        private int touches;
        private int prevCodeSmells;
        private boolean prevBuggy;
        private int startLine;
        private int endLine;

        public Builder projectName(String projectName) {
            this.projectName = Objects.requireNonNull(projectName, "projectName"); return this;
        }
        public Builder path(String path) {
            this.path = Objects.requireNonNull(path, "path"); return this;
        }
        public Builder methodSignature(String methodSignature) {
            this.methodSignature = Objects.requireNonNull(methodSignature, "methodSignature"); return this;
        }
        public Builder releaseId(String releaseId) {
            this.releaseId = Objects.requireNonNull(releaseId, "releaseId"); return this;
        }
        public Builder metrics(MethodMetrics metrics) {
            this.metrics = Objects.requireNonNull(metrics, "metrics"); return this;
        }
        public Builder commitHashes(List<String> hashes) {
            this.commitHashes = List.copyOf(Objects.requireNonNull(hashes, "commitHashes")); return this;
        }
        public Builder buggy(boolean buggy) {
            this.buggy = buggy; return this;
        }
        public Builder codeSmells(int cs) {
            this.codeSmells = cs;
            return this;
        }
        public Builder touches(int t) {
            this.touches = t;
            return this;
        }
        public Builder prevCodeSmells(int cs) {
            this.prevCodeSmells = cs;
            return this;
        }
        public Builder prevBuggy(boolean b) {
            this.prevBuggy = b;
            return this;
        }
        public Builder startLine(int line) {
            this.startLine = line; return this;
        }
        public Builder endLine(int line) {
            this.endLine = line; return this;
        }

        private void validate() {
            Objects.requireNonNull(projectName,     "projectName missing");
            Objects.requireNonNull(path,            "path missing");
            Objects.requireNonNull(methodSignature, "methodSignature missing");
            Objects.requireNonNull(releaseId,       "releaseId missing");
            Objects.requireNonNull(metrics,         "metrics missing");
            Objects.requireNonNull(commitHashes,    "commitHashes missing");
        }

        public MethodData build() {
            validate();
            return new MethodData(this);
        }
    }
}