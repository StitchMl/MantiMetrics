package com.mantimetrics.dataset;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.stream.Collectors;

public final class DatasetArffWriter {

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

    private String attributeType(String column) {
        return DatasetColumns.isNominalColumn(column) ? "{yes,no}" : "numeric";
    }

    private String formatValue(String column, String value) {
        if (value == null || value.isBlank()) {
            return "?";
        }
        return DatasetColumns.isNominalColumn(column) ? value : value.trim();
    }

    private String escape(String value) {
        return value.replace("'", "\\'");
    }
}
