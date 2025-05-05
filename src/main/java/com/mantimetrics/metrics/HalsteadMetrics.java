package com.mantimetrics.metrics;

/**
 * Immutable object che contiene tutte le metriche di Halstead.
 */
public class HalsteadMetrics {
    private final int n1, n2, totalN1, totalN2;
    private final double vocabulary, length, volume, difficulty, effort;

    private HalsteadMetrics(Builder b) {
        this.n1         = b.n1;
        this.n2         = b.n2;
        this.totalN1 = b.totalN1;
        this.totalN2 = b.totalN2;
        this.vocabulary = b.vocabulary;
        this.length     = b.length;
        this.volume     = b.volume;
        this.difficulty = b.difficulty;
        this.effort     = b.effort;
    }

    // getters...
    public int getDistinctOperators()   { return n1; }
    public int getDistinctOperands()    { return n2; }
    public int getTotalOperators()      { return totalN1; }
    public int getTotalOperands()       { return totalN2; }
    public double getVocabulary()       { return vocabulary; }
    public double getLength()           { return length; }
    public double getVolume()           { return volume; }
    public double getDifficulty()       { return difficulty; }
    public double getEffort()           { return effort; }

    public static class Builder {
        private int n1, n2, totalN1, totalN2;
        private double vocabulary, length, volume, difficulty, effort;

        public Builder n1(int n1) {
            this.n1 = n1; return this;
        }
        public Builder n2(int n2) {
            this.n2 = n2; return this;
        }
        public Builder totalN1(int totalN1) {
            this.totalN1 = totalN1; return this;
        }
        public Builder totalN2(int totalN2) {
            this.totalN2 = totalN2; return this;
        }
        public Builder vocabulary(double v) {
            this.vocabulary = v; return this;
        }
        public Builder length(double l) {
            this.length = l; return this;
        }
        public Builder volume(double v) {
            this.volume = v; return this;
        }
        public Builder difficulty(double d) {
            this.difficulty = d; return this;
        }
        public Builder effort(double e) {
            this.effort = e; return this;
        }

        private void validate() {
            if (vocabulary < 0 || length < 0) {
                throw new IllegalStateException("Halstead metrics invalid");
            }
        }

        public HalsteadMetrics build() {
            validate();
            return new HalsteadMetrics(this);
        }
    }
}