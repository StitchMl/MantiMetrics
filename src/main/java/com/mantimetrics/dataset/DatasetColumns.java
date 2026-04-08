package com.mantimetrics.dataset;

import java.util.List;
import java.util.Set;

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

    private DatasetColumns() {
        throw new AssertionError("Do not instantiate DatasetColumns");
    }

    public static List<String> identifierColumns() {
        return IDENTIFIER_COLUMNS;
    }

    public static List<String> actionableColumns() {
        return ACTIONABLE_COLUMNS;
    }

    public static List<String> classifierColumns(List<String> rawHeader) {
        return rawHeader.stream()
                .filter(column -> !IDENTIFIER_COLUMN_SET.contains(column))
                .toList();
    }

    @SuppressWarnings("unused")
    public static boolean isActionableColumn(String column) {
        return ACTIONABLE_COLUMNS.contains(column);
    }

    public static boolean isNominalColumn(String column) {
        return NOMINAL_COLUMNS.contains(column);
    }
}
