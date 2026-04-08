package com.mantimetrics.dataset;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

public record DatasetTable(List<String> header, List<Map<String, String>> rows) {

    public DatasetTable {
        header = List.copyOf(header);
        rows = rows.stream()
                .map(DatasetTable::immutableRowCopy)
                .toList();
    }

    public DatasetTable selectColumns(List<String> selectedColumns) {
        List<String> orderedHeader = header.stream()
                .filter(selectedColumns::contains)
                .toList();
        List<Map<String, String>> projectedRows = rows.stream()
                .map(row -> projectRow(row, orderedHeader))
                .toList();
        return new DatasetTable(orderedHeader, projectedRows);
    }

    public DatasetTable filter(Predicate<Map<String, String>> predicate) {
        return new DatasetTable(header, rows.stream()
                .filter(predicate)
                .toList());
    }

    public DatasetTable mapRows(UnaryOperator<Map<String, String>> mapper) {
        return new DatasetTable(header, rows.stream()
                .map(row -> immutableRowCopy(mapper.apply(row)))
                .toList());
    }

    public int rowCount() {
        return rows.size();
    }

    private static Map<String, String> projectRow(Map<String, String> row, List<String> orderedHeader) {
        Map<String, String> projected = new LinkedHashMap<>();
        for (String column : orderedHeader) {
            projected.put(column, row.getOrDefault(column, ""));
        }
        return projected;
    }

    private static Map<String, String> immutableRowCopy(Map<String, String> row) {
        Map<String, String> copy = new LinkedHashMap<>(row);
        return Map.copyOf(copy);
    }
}
