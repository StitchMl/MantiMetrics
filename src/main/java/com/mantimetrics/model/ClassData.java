package com.mantimetrics.model;

import com.mantimetrics.metrics.MethodMetrics;
import java.util.*;
import java.util.StringJoiner;

@SuppressWarnings("unused")
public class ClassData implements DatasetRow {
    private final String projectName;
    private final String path;
    private final String className;
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

    private ClassData(Builder b) {
        this.projectName = b.projectName;
        this.path = b.path;
        this.className = b.className;
        this.releaseId = b.releaseId;
        this.metrics = b.metrics;
        this.commitHashes = List.copyOf(b.commitHashes);
        this.buggy = b.buggy;
        this.codeSmells = b.codeSmells;
        this.touches = b.touches;
        this.prevCodeSmells = b.prevCodeSmells;
        this.prevBuggy = b.prevBuggy;
        this.startLine = b.startLine;
        this.endLine = b.endLine;
    }

    public String getProjectName() { return projectName; }
    public String getPath() { return path; }
    public String getClassName() { return className; }
    public String getReleaseId() { return releaseId; }
    public MethodMetrics getMetrics() { return metrics; }
    public List<String> getCommitHashes() { return commitHashes; }
    public boolean isBuggy() { return buggy; }
    public int getCodeSmells() { return codeSmells; }
    public int getTouches() { return touches; }
    public int getPrevCodeSmells() { return prevCodeSmells; }
    public boolean isPrevBuggy() { return prevBuggy; }
    public int getStartLine() { return startLine; }
    public int getEndLine() { return endLine; }

    public String getUniqueKey() { return path + "#" + className; }

    @Override
    public String toCsvLine() {
        StringJoiner sj = new StringJoiner(",");
        sj.add(projectName)
                .add(path)
                .add('"' + className.replace("\"", "\"\"") + '"')
                .add(releaseId);

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
                .add(metrics.isLongMethod() ? "1" : "0")
                .add(metrics.isGodClass() ? "1" : "0")
                .add(metrics.isFeatureEnvy() ? "1" : "0")
                .add(metrics.isDuplicatedCode() ? "1" : "0")
                .add(String.valueOf(codeSmells))
                .add(String.valueOf(touches))
                .add(String.valueOf(prevCodeSmells))
                .add(prevBuggy ? "yes" : "no")
                .add(buggy ? "yes" : "no");
        return sj.toString();
    }

    public Builder toBuilder() {
        return new Builder()
                .projectName(projectName)
                .path(path)
                .className(className)
                .releaseId(releaseId)
                .metrics(metrics)
                .commitHashes(commitHashes)
                .buggy(buggy)
                .codeSmells(codeSmells)
                .touches(touches)
                .prevCodeSmells(prevCodeSmells)
                .prevBuggy(prevBuggy)
                .startLine(startLine)
                .endLine(endLine);
    }

    public static class Builder {
        private String projectName;
        private String path;
        private String className;
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

        public Builder projectName(String v){ projectName=Objects.requireNonNull(v); return this; }
        public Builder path(String v){ path=Objects.requireNonNull(v); return this; }
        public Builder className(String v){ className=Objects.requireNonNull(v); return this; }
        public Builder releaseId(String v){ releaseId=Objects.requireNonNull(v); return this; }
        public Builder metrics(MethodMetrics v){ metrics=Objects.requireNonNull(v); return this; }
        public Builder commitHashes(List<String> v){ commitHashes=Objects.requireNonNull(v); return this; }
        public Builder buggy(boolean v){ buggy=v; return this; }
        public Builder codeSmells(int v){ codeSmells=v; return this; }
        public Builder touches(int v){ touches=v; return this; }
        public Builder prevCodeSmells(int v){ prevCodeSmells=v; return this; }
        public Builder prevBuggy(boolean v){ prevBuggy=v; return this; }
        public Builder startLine(int v){ startLine=v; return this; }
        public Builder endLine(int v){ endLine=v; return this; }

        public ClassData build() {
            Objects.requireNonNull(projectName);
            Objects.requireNonNull(path);
            Objects.requireNonNull(className);
            Objects.requireNonNull(releaseId);
            Objects.requireNonNull(metrics);
            return new ClassData(this);
        }
    }
}