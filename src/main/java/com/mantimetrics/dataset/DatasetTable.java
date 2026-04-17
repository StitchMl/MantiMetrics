package com.mantimetrics.dataset;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

/**
 * Immutable in-memory representation of a dataset table.
 *
 * @param header ordered dataset columns
 * @param rows immutable dataset rows
 */
public record DatasetTable(List<String> header, List<Map<String, String>> rows) {

    /**
     * Creates an immutable dataset table copying header and rows defensively.
     *
     * @param header ordered dataset columns
     * @param rows dataset rows
     */
    public DatasetTable {
        header = List.copyOf(header);
        rows = rows.stream()
                .map(DatasetTable::immutableRowCopy)
                .toList();
    }

    /**
     * Projects the dataset onto a subset of columns preserving the original header order.
     *
     * @param selectedColumns columns to keep
     * @return projected dataset table
     */
    public DatasetTable selectColumns(List<String> selectedColumns) {
        List<String> orderedHeader = header.stream()
                .filter(selectedColumns::contains)
                .toList();
        List<Map<String, String>> projectedRows = rows.stream()
                .map(row -> projectRow(row, orderedHeader))
                .toList();
        return new DatasetTable(orderedHeader, projectedRows);
    }

    /**
     * Filters the dataset rows with the supplied predicate.
     *
     * @param predicate row predicate
     * @return filtered dataset table
     */
    public DatasetTable filter(Predicate<Map<String, String>> predicate) {
        return new DatasetTable(header, rows.stream()
                .filter(predicate)
                .toList());
    }

    /**
     * Maps each row to a new immutable row while preserving the header.
     *
     * @param mapper row transformer
     * @return transformed dataset table
     */
    public DatasetTable mapRows(UnaryOperator<Map<String, String>> mapper) {
        return new DatasetTable(header, rows.stream()
                .map(row -> immutableRowCopy(mapper.apply(row)))
                .toList());
    }

    /**
     * Returns the number of rows in the dataset.
     *
     * @return row count
     */
    public int rowCount() {
        return rows.size();
    }

    /**
     * Projects one row onto the requested ordered header.
     *
     * @param row source row
     * @param orderedHeader target header order
     * @return projected row
     */
    private static Map<String, String> projectRow(Map<String, String> row, List<String> orderedHeader) {
        Map<String, String> projected = new LinkedHashMap<>();
        for (String column : orderedHeader) {
            projected.put(column, row.getOrDefault(column, ""));
        }
        return projected;
    }

    /**
     * Copies a row into an immutable linked map.
     *
     * @param row source row
     * @return immutable row copy
     */
    private static Map<String, String> immutableRowCopy(Map<String, String> row) {
        Map<String, String> copy = new LinkedHashMap<>(row);
        return Map.copyOf(copy);
    }
}
