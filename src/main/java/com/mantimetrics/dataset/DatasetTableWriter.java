package com.mantimetrics.dataset;

import com.opencsv.ICSVWriter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Writes dataset tables in CSV format.
 */
public final class DatasetTableWriter {

    /**
     * Writes a dataset table to a CSV file.
     *
     * @param outputPath target CSV path
     * @param table dataset table to serialize
     * @throws IOException when the output file cannot be written
     */
    public void write(Path outputPath, DatasetTable table) throws IOException {
        Files.createDirectories(outputPath.getParent());
        try (ICSVWriter writer = new com.opencsv.CSVWriter(Files.newBufferedWriter(
                outputPath,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING
        ))) {
            writer.writeNext(table.header().toArray(String[]::new), false);
            for (var row : table.rows()) {
                writer.writeNext(table.header().stream()
                        .map(column -> row.getOrDefault(column, ""))
                        .toArray(String[]::new), false);
            }
        }
    }
}
