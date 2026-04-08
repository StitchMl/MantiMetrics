package com.mantimetrics.metrics;

public final class MethodMetricsBuilder {
    private int loc;
    private int stmtCount;
    private int cyclomatic;
    private int cognitive;
    private int distinctOperators;
    private int distinctOperands;
    private int totalOperators;
    private int totalOperands;
    private double vocabulary;
    private double length;
    private double volume;
    private double difficulty;
    private double effort;
    private int maxNestingDepth;
    private boolean longMethod;
    private boolean godClass;
    private boolean featureEnvy;
    private boolean duplicatedCode;

    public MethodMetricsBuilder loc(int value) { this.loc = value; return this; }
    public MethodMetricsBuilder stmtCount(int value) { this.stmtCount = value; return this; }
    public MethodMetricsBuilder cyclomatic(int value) { this.cyclomatic = value; return this; }
    public MethodMetricsBuilder cognitive(int value) { this.cognitive = value; return this; }
    public MethodMetricsBuilder distinctOperators(int value) { this.distinctOperators = value; return this; }
    public MethodMetricsBuilder distinctOperands(int value) { this.distinctOperands = value; return this; }
    public MethodMetricsBuilder totalOperators(int value) { this.totalOperators = value; return this; }
    public MethodMetricsBuilder totalOperands(int value) { this.totalOperands = value; return this; }
    public MethodMetricsBuilder vocabulary(double value) { this.vocabulary = value; return this; }
    public MethodMetricsBuilder length(double value) { this.length = value; return this; }
    public MethodMetricsBuilder volume(double value) { this.volume = value; return this; }
    public MethodMetricsBuilder difficulty(double value) { this.difficulty = value; return this; }
    public MethodMetricsBuilder effort(double value) { this.effort = value; return this; }
    public MethodMetricsBuilder maxNestingDepth(int value) { this.maxNestingDepth = value; return this; }
    public MethodMetricsBuilder longMethod(boolean value) { this.longMethod = value; return this; }
    public MethodMetricsBuilder godClass(boolean value) { this.godClass = value; return this; }
    public MethodMetricsBuilder featureEnvy(boolean value) { this.featureEnvy = value; return this; }
    public MethodMetricsBuilder duplicatedCode(boolean value) { this.duplicatedCode = value; return this; }

    public MethodMetricsBuilder halstead(HalsteadMetrics halsteadMetrics) {
        return distinctOperators(halsteadMetrics.getDistinctOperators())
                .distinctOperands(halsteadMetrics.getDistinctOperands())
                .totalOperators(halsteadMetrics.getTotalOperators())
                .totalOperands(halsteadMetrics.getTotalOperands())
                .vocabulary(halsteadMetrics.getVocabulary())
                .length(halsteadMetrics.getLength())
                .volume(halsteadMetrics.getVolume())
                .difficulty(halsteadMetrics.getDifficulty())
                .effort(halsteadMetrics.getEffort());
    }

    public MethodMetrics build() {
        return new MethodMetrics(
                loc,
                stmtCount,
                cyclomatic,
                cognitive,
                distinctOperators,
                distinctOperands,
                totalOperators,
                totalOperands,
                vocabulary,
                length,
                volume,
                difficulty,
                effort,
                maxNestingDepth,
                longMethod,
                godClass,
                featureEnvy,
                duplicatedCode
        );
    }
}
