package com.mantimetrics.metrics;

/**
 * Fluent builder for immutable {@link MethodMetrics} instances.
 */
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

    /**
     * Sets the lines of code.
     *
     * @param value lines of code
     * @return current builder
     */
    public MethodMetricsBuilder loc(int value) { this.loc = value; return this; }

    /**
     * Sets the statement count.
     *
     * @param value statement count
     * @return current builder
     */
    public MethodMetricsBuilder stmtCount(int value) { this.stmtCount = value; return this; }

    /**
     * Sets the cyclomatic complexity.
     *
     * @param value cyclomatic complexity
     * @return current builder
     */
    public MethodMetricsBuilder cyclomatic(int value) { this.cyclomatic = value; return this; }

    /**
     * Sets the cognitive complexity.
     *
     * @param value cognitive complexity
     * @return current builder
     */
    public MethodMetricsBuilder cognitive(int value) { this.cognitive = value; return this; }

    /**
     * Sets the distinct Halstead operators.
     *
     * @param value distinct operators
     * @return current builder
     */
    public MethodMetricsBuilder distinctOperators(int value) { this.distinctOperators = value; return this; }

    /**
     * Sets the distinct Halstead operands.
     *
     * @param value distinct operands
     * @return current builder
     */
    public MethodMetricsBuilder distinctOperands(int value) { this.distinctOperands = value; return this; }

    /**
     * Sets the total Halstead operators.
     *
     * @param value total operators
     * @return current builder
     */
    public MethodMetricsBuilder totalOperators(int value) { this.totalOperators = value; return this; }

    /**
     * Sets the total Halstead operands.
     *
     * @param value total operands
     * @return current builder
     */
    public MethodMetricsBuilder totalOperands(int value) { this.totalOperands = value; return this; }

    /**
     * Sets the Halstead vocabulary.
     *
     * @param value vocabulary
     * @return current builder
     */
    public MethodMetricsBuilder vocabulary(double value) { this.vocabulary = value; return this; }

    /**
     * Sets the Halstead length.
     *
     * @param value length
     * @return current builder
     */
    public MethodMetricsBuilder length(double value) { this.length = value; return this; }

    /**
     * Sets the Halstead volume.
     *
     * @param value volume
     * @return current builder
     */
    public MethodMetricsBuilder volume(double value) { this.volume = value; return this; }

    /**
     * Sets the Halstead difficulty.
     *
     * @param value difficulty
     * @return current builder
     */
    public MethodMetricsBuilder difficulty(double value) { this.difficulty = value; return this; }

    /**
     * Sets the Halstead effort.
     *
     * @param value effort
     * @return current builder
     */
    public MethodMetricsBuilder effort(double value) { this.effort = value; return this; }

    /**
     * Sets the maximum nesting depth.
     *
     * @param value maximum nesting depth
     * @return current builder
     */
    public MethodMetricsBuilder maxNestingDepth(int value) { this.maxNestingDepth = value; return this; }

    /**
     * Sets the long-method flag.
     *
     * @param value long-method flag
     * @return current builder
     */
    public MethodMetricsBuilder longMethod(boolean value) { this.longMethod = value; return this; }

    /**
     * Sets the God-Class flag.
     *
     * @param value God-Class flag
     * @return current builder
     */
    public MethodMetricsBuilder godClass(boolean value) { this.godClass = value; return this; }

    /**
     * Sets the Feature-Envy flag.
     *
     * @param value Feature-Envy flag
     * @return current builder
     */
    public MethodMetricsBuilder featureEnvy(boolean value) { this.featureEnvy = value; return this; }

    /**
     * Sets the duplicated-code flag.
     *
     * @param value duplicated-code flag
     * @return current builder
     */
    public MethodMetricsBuilder duplicatedCode(boolean value) { this.duplicatedCode = value; return this; }

    /**
     * Copies the Halstead metrics from the provided aggregate.
     *
     * @param halsteadMetrics Halstead metrics aggregate
     * @return current builder
     */
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

    /**
     * Builds the immutable metrics aggregate.
     *
     * @return immutable method metrics
     */
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
