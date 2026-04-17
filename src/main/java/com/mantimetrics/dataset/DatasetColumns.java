package com.mantimetrics.dataset;

import java.util.List;
import java.util.Set;

/**
 * Shared column names and column-group helpers for the dataset artifacts.
 */
public final class DatasetColumns {
    public static final String BUGGY = "Buggy";
    public static final String NSMELLS = "NSmells";

    private static final List<String> IDENTIFIER_COLUMNS = List.of(
            "Project", "Path", "Method", "Class", "ReleaseId"
    );
    private static final List<String> ACTIONABLE_COLUMNS = List.of(
            "CodeSmells", NSMELLS, "isLongMethod", "isGodClass", "isFeatureEnvy", "isDuplicatedCode"
    );
    private static final Set<String> IDENTIFIER_COLUMN_SET = Set.copyOf(IDENTIFIER_COLUMNS);
    private static final Set<String> NOMINAL_COLUMNS = Set.of("prevBuggy", BUGGY);

    /**
     * Prevents instantiation of the static utility class.
     */
    private DatasetColumns() {
        throw new AssertionError("Do not instantiate DatasetColumns");
    }

    /**
     * Returns the identifier columns preserved in the raw dataset.
     *
     * @return identifier columns
     */
    public static List<String> identifierColumns() {
        return IDENTIFIER_COLUMNS;
    }

    /**
     * Returns the actionable smell-related columns used by the what-if datasets.
     *
     * @return actionable columns
     */
    public static List<String> actionableColumns() {
        return ACTIONABLE_COLUMNS;
    }

    /**
     * Returns the columns used to train classifiers, excluding identifiers.
     *
     * @param rawHeader raw dataset header
     * @return classifier-ready columns
     */
    public static List<String> classifierColumns(List<String> rawHeader) {
        return rawHeader.stream()
                .filter(column -> !IDENTIFIER_COLUMN_SET.contains(column))
                .toList();
    }

    /**
     * Reports whether a column belongs to the actionable smell subset.
     *
     * @param column column name to inspect
     * @return {@code true} when the column is actionable
     */
    @SuppressWarnings("unused")
    public static boolean isActionableColumn(String column) {
        return ACTIONABLE_COLUMNS.contains(column);
    }

    /**
     * Reports whether a column must be treated as nominal in ARFF exports.
     *
     * @param column column name to inspect
     * @return {@code true} when the column is nominal
     */
    public static boolean isNominalColumn(String column) {
        return NOMINAL_COLUMNS.contains(column);
    }
}
