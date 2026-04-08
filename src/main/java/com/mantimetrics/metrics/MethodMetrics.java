package com.mantimetrics.metrics;

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
    public static MethodMetricsBuilder builder() {
        return new MethodMetricsBuilder();
    }

    public int getLoc() { return loc; }
    public int getStmtCount() { return stmtCount; }
    public int getCyclomatic() { return cyclomatic; }
    public int getCognitive() { return cognitive; }
    public int getDistinctOperators() { return distinctOperators; }
    public int getDistinctOperands() { return distinctOperands; }
    public int getTotalOperators() { return totalOperators; }
    public int getTotalOperands() { return totalOperands; }
    public double getVocabulary() { return vocabulary; }
    public double getLength() { return length; }
    public double getVolume() { return volume; }
    public double getDifficulty() { return difficulty; }
    public double getEffort() { return effort; }
    public int getMaxNestingDepth() { return maxNestingDepth; }
    public boolean isLongMethod() { return longMethod; }
    public boolean isGodClass() { return godClass; }
    public boolean isFeatureEnvy() { return featureEnvy; }
    public boolean isDuplicatedCode() { return duplicatedCode; }
}
