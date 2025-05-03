package com.mantimetrics.metrics;

public class MethodMetrics {
    // Basic Metrics
    private int loc;
    private int stmtCount;
    private int cyclomatic;
    private int cognitive;

    // Halstead Metrics
    private int distinctOperators;   // n1
    private int distinctOperands;    // n2
    private int totalOperators;      // N1
    private int totalOperands;       // N2
    private double vocabulary;       // n = n1 + n2
    private double length;           // N = N1 + N2
    private double volume;           // V = N * log2(n)
    private double difficulty;       // D = (n1 / 2) * (N2 / n2)
    private double effort;           // E = D * V

    // Nesting depth
    private int maxNestingDepth;

    // Queue indicators smell
    private boolean isLongMethod;
    private boolean isGodClass;
    private boolean isFeatureEnvy;
    private boolean isDuplicatedCode;

    // Getters e Setters
    public int getLoc() { return loc; }
    public void setLoc(int loc) { this.loc = loc; }

    public int getStmtCount() { return stmtCount; }
    public void setStmtCount(int stmtCount) { this.stmtCount = stmtCount; }

    public int getCyclomatic() { return cyclomatic; }
    public void setCyclomatic(int cyclomatic) { this.cyclomatic = cyclomatic; }

    public int getCognitive() { return cognitive; }
    public void setCognitive(int cognitive) { this.cognitive = cognitive; }

    public int getDistinctOperators() { return distinctOperators; }
    public void setDistinctOperators(int distinctOperators) { this.distinctOperators = distinctOperators; }

    public int getDistinctOperands() { return distinctOperands; }
    public void setDistinctOperands(int distinctOperands) { this.distinctOperands = distinctOperands; }

    public int getTotalOperators() { return totalOperators; }
    public void setTotalOperators(int totalOperators) { this.totalOperators = totalOperators; }

    public int getTotalOperands() { return totalOperands; }
    public void setTotalOperands(int totalOperands) { this.totalOperands = totalOperands; }

    public double getVocabulary() { return vocabulary; }
    public void setVocabulary(double vocabulary) { this.vocabulary = vocabulary; }

    public double getLength() { return length; }
    public void setLength(double length) { this.length = length; }

    public double getVolume() { return volume; }
    public void setVolume(double volume) { this.volume = volume; }

    public double getDifficulty() { return difficulty; }
    public void setDifficulty(double difficulty) { this.difficulty = difficulty; }

    public double getEffort() { return effort; }
    public void setEffort(double effort) { this.effort = effort; }

    public int getMaxNestingDepth() { return maxNestingDepth; }
    public void setMaxNestingDepth(int maxNestingDepth) { this.maxNestingDepth = maxNestingDepth; }

    public boolean isLongMethod() { return isLongMethod; }
    public void setLongMethod(boolean isLongMethod) { this.isLongMethod = isLongMethod; }

    public boolean isGodClass() { return isGodClass; }
    public void setGodClass(boolean isGodClass) { this.isGodClass = isGodClass; }

    public boolean isFeatureEnvy() { return isFeatureEnvy; }
    public void setFeatureEnvy(boolean isFeatureEnvy) { this.isFeatureEnvy = isFeatureEnvy; }

    public boolean isDuplicatedCode() { return isDuplicatedCode; }
    public void setDuplicatedCode(boolean isDuplicatedCode) { this.isDuplicatedCode = isDuplicatedCode; }
}