package com.mantimetrics.dataset;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.stream.Collectors;

/**
 * Writes dataset tables in Weka-compatible ARFF format.
 */
public final class DatasetArffWriter {

    /**
     * Writes a dataset table to an ARFF file.
     *
     * @param outputPath target ARFF path
     * @param relationName ARFF relation name
     * @param table dataset table to serialize
     * @throws IOException when the output file cannot be written
     */
    public void write(Path outputPath, String relationName, DatasetTable table) throws IOException {
        Files.createDirectories(outputPath.getParent());
        try (BufferedWriter writer = Files.newBufferedWriter(
                outputPath,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING
        )) {
            writer.write("@relation '" + escape(relationName) + "'");
            writer.newLine();
            writer.newLine();

            for (String column : table.header()) {
                writer.write("@attribute '" + escape(column) + "' " + attributeType(column));
                writer.newLine();
            }

            writer.newLine();
            writer.write("@data");
            writer.newLine();

            for (var row : table.rows()) {
                writer.write(table.header().stream()
                        .map(column -> formatValue(column, row.getOrDefault(column, "")))
                        .collect(Collectors.joining(",")));
                writer.newLine();
            }
        }
    }

    /**
     * Resolves the ARFF attribute type for a dataset column.
     *
     * @param column dataset column name
     * @return ARFF attribute type
     */
    private String attributeType(String column) {
        return DatasetColumns.isNominalColumn(column) ? "{yes,no}" : "numeric";
    }

    /**
     * Formats one cell value for ARFF output.
     *
     * @param column dataset column name
     * @param value raw cell value
     * @return ARFF cell value or {@code ?} for missing values
     */
    private String formatValue(String column, String value) {
        if (value == null || value.isBlank()) {
            return "?";
        }
        return DatasetColumns.isNominalColumn(column) ? value : value.trim();
    }

    /**
     * Escapes single quotes in ARFF identifiers.
     *
     * @param value identifier to escape
     * @return escaped ARFF identifier
     */
    private String escape(String value) {
        return value.replace("'", "\\'");
    }
}
