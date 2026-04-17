package com.mantimetrics.metrics;

/**
 * Immutable aggregate of the static metrics and smell flags computed for one entity.
 *
 * @param loc lines of code
 * @param stmtCount statement count
 * @param cyclomatic cyclomatic complexity
 * @param cognitive cognitive complexity
 * @param distinctOperators distinct Halstead operators
 * @param distinctOperands distinct Halstead operands
 * @param totalOperators total Halstead operators
 * @param totalOperands total Halstead operands
 * @param vocabulary Halstead vocabulary
 * @param length Halstead length
 * @param volume Halstead volume
 * @param difficulty Halstead difficulty
 * @param effort Halstead effort
 * @param maxNestingDepth maximum nesting depth
 * @param longMethod long-method smell flag
 * @param godClass God-Class smell flag
 * @param featureEnvy Feature-Envy smell flag
 * @param duplicatedCode duplicated-code smell flag
 */
public record MethodMetrics(
        int loc,
        int stmtCount,
        int cyclomatic,
        int cognitive,
        int distinctOperators,
        int distinctOperands,
        int totalOperators,
        int totalOperands,
        double vocabulary,
        double length,
        double volume,
        double difficulty,
        double effort,
        int maxNestingDepth,
        boolean longMethod,
        boolean godClass,
        boolean featureEnvy,
        boolean duplicatedCode
) {
    /**
     * Returns a fluent builder for method metrics.
     *
     * @return new builder instance
     */
    public static MethodMetricsBuilder builder() {
        return new MethodMetricsBuilder();
    }

    /**
     * Returns the lines of code.
     *
     * @return lines of code
     */
    public int getLoc() { return loc; }

    /**
     * Returns the statement count.
     *
     * @return statement count
     */
    public int getStmtCount() { return stmtCount; }

    /**
     * Returns the cyclomatic complexity.
     *
     * @return cyclomatic complexity
     */
    public int getCyclomatic() { return cyclomatic; }

    /**
     * Returns the cognitive complexity.
     *
     * @return cognitive complexity
     */
    public int getCognitive() { return cognitive; }

    /**
     * Returns the distinct Halstead operators.
     *
     * @return distinct operators
     */
    public int getDistinctOperators() { return distinctOperators; }

    /**
     * Returns the distinct Halstead operands.
     *
     * @return distinct operands
     */
    public int getDistinctOperands() { return distinctOperands; }

    /**
     * Returns the total Halstead operators.
     *
     * @return total operators
     */
    public int getTotalOperators() { return totalOperators; }

    /**
     * Returns the total Halstead operands.
     *
     * @return total operands
     */
    public int getTotalOperands() { return totalOperands; }

    /**
     * Returns the Halstead vocabulary.
     *
     * @return vocabulary
     */
    public double getVocabulary() { return vocabulary; }

    /**
     * Returns the Halstead length.
     *
     * @return length
     */
    public double getLength() { return length; }

    /**
     * Returns the Halstead volume.
     *
     * @return volume
     */
    public double getVolume() { return volume; }

    /**
     * Returns the Halstead difficulty.
     *
     * @return difficulty
     */
    public double getDifficulty() { return difficulty; }

    /**
     * Returns the Halstead effort.
     *
     * @return effort
     */
    public double getEffort() { return effort; }

    /**
     * Returns the maximum nesting depth.
     *
     * @return maximum nesting depth
     */
    public int getMaxNestingDepth() { return maxNestingDepth; }

    /**
     * Reports whether the entity is a long method.
     *
     * @return long-method flag
     */
    public boolean isLongMethod() { return longMethod; }

    /**
     * Reports whether the entity belongs to a God Class.
     *
     * @return God-Class flag
     */
    public boolean isGodClass() { return godClass; }

    /**
     * Reports whether the entity exhibits Feature Envy.
     *
     * @return Feature-Envy flag
     */
    public boolean isFeatureEnvy() { return featureEnvy; }

    /**
     * Reports whether the entity exhibits duplicated code.
     *
     * @return duplicated-code flag
     */
    public boolean isDuplicatedCode() { return duplicatedCode; }
}
