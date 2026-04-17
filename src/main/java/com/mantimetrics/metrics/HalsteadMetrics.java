package com.mantimetrics.metrics;

/**
 * Immutable container for Halstead metrics.
 */
public class HalsteadMetrics {
    private final int n1;
    private final int n2;
    private final int totalN1;
    private final int totalN2;
    private final double vocabulary;
    private final double length;
    private final double volume;
    private final double difficulty;
    private final double effort;

    /**
     * Creates an immutable Halstead-metrics aggregate from the builder values.
     *
     * @param b builder providing the metric values
     */
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

    /**
     * Returns the number of distinct operators.
     *
     * @return distinct operators
     */
    public int getDistinctOperators()   { return n1; }

    /**
     * Returns the number of distinct operands.
     *
     * @return distinct operands
     */
    public int getDistinctOperands()    { return n2; }

    /**
     * Returns the total number of operators.
     *
     * @return total operators
     */
    public int getTotalOperators()      { return totalN1; }

    /**
     * Returns the total number of operands.
     *
     * @return total operands
     */
    public int getTotalOperands()       { return totalN2; }

    /**
     * Returns the Halstead vocabulary.
     *
     * @return vocabulary
     */
    public double getVocabulary()       { return vocabulary; }

    /**
     * Returns the Halstead length.
     *
     * @return length
     */
    public double getLength()           { return length; }

    /**
     * Returns the Halstead volume.
     *
     * @return volume
     */
    public double getVolume()           { return volume; }

    /**
     * Returns the Halstead difficulty.
     *
     * @return difficulty
     */
    public double getDifficulty()       { return difficulty; }

    /**
     * Returns the Halstead effort.
     *
     * @return effort
     */
    public double getEffort()           { return effort; }

    /**
     * Fluent builder for {@link HalsteadMetrics}.
     */
    public static class Builder {
        private int n1;
        private int n2;
        private int totalN1;
        private int totalN2;
        private double vocabulary;
        private double length;
        private double volume;
        private double difficulty;
        private double effort;

        /**
         * Sets the number of distinct operators.
         *
         * @param n1 distinct operators
         * @return current builder
         */
        public Builder n1(int n1) {
            this.n1 = n1; return this;
        }

        /**
         * Sets the number of distinct operands.
         *
         * @param n2 distinct operands
         * @return current builder
         */
        public Builder n2(int n2) {
            this.n2 = n2; return this;
        }

        /**
         * Sets the total number of operators.
         *
         * @param totalN1 total operators
         * @return current builder
         */
        public Builder totalN1(int totalN1) {
            this.totalN1 = totalN1; return this;
        }

        /**
         * Sets the total number of operands.
         *
         * @param totalN2 total operands
         * @return current builder
         */
        public Builder totalN2(int totalN2) {
            this.totalN2 = totalN2; return this;
        }

        /**
         * Sets the Halstead vocabulary.
         *
         * @param v vocabulary
         * @return current builder
         */
        public Builder vocabulary(double v) {
            this.vocabulary = v; return this;
        }

        /**
         * Sets the Halstead length.
         *
         * @param l length
         * @return current builder
         */
        public Builder length(double l) {
            this.length = l; return this;
        }

        /**
         * Sets the Halstead volume.
         *
         * @param v volume
         * @return current builder
         */
        public Builder volume(double v) {
            this.volume = v; return this;
        }

        /**
         * Sets the Halstead difficulty.
         *
         * @param d difficulty
         * @return current builder
         */
        public Builder difficulty(double d) {
            this.difficulty = d; return this;
        }

        /**
         * Sets the Halstead effort.
         *
         * @param e effort
         * @return current builder
         */
        public Builder effort(double e) {
            this.effort = e; return this;
        }

        /**
         * Validates that the current builder state is internally consistent.
         */
        private void validate() {
            if (vocabulary < 0 || length < 0) {
                throw new IllegalStateException("Halstead metrics invalid");
            }
        }

        /**
         * Builds the immutable Halstead metrics aggregate.
         *
         * @return immutable Halstead metrics
         */
        public HalsteadMetrics build() {
            validate();
            return new HalsteadMetrics(this);
        }
    }
}
