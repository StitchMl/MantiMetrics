package com.mantimetrics.model;

/**
 * Common contract implemented by every dataset row emitted by the analysis pipeline.
 */
@SuppressWarnings("unused")
public interface DatasetRow {
    /**
     * Returns the stable identifier used to track the row across releases.
     *
     * @return unique row identifier
     */
    String getUniqueKey();

    /**
     * Returns the normalized relative source path of the entity.
     *
     * @return normalized relative source path
     */
    String getPath();

    /**
     * Returns the first source line covered by the entity.
     *
     * @return inclusive start line
     */
    int getStartLine();

    /**
     * Returns the last source line covered by the entity.
     *
     * @return inclusive end line
     */
    int getEndLine();

    /**
     * Returns the number of PMD code smells associated with the entity.
     *
     * @return PMD code smell count
     */
    int getCodeSmells();

    /**
     * Returns the total smell count combining PMD violations and binary smell detectors.
     *
     * @return total smell count
     */
    int getNSmells();

    /**
     * Reports whether the row is historically labeled as buggy.
     *
     * @return {@code true} when the row is buggy
     */
    boolean isBuggy();

    /**
     * Serializes the row into one CSV line matching the dataset header.
     *
     * @return CSV representation of the row
     */
    String toCsvLine();
}
