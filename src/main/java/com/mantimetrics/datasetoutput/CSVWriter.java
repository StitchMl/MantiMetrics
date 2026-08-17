package com.mantimetrics.datasetoutput;

import com.mantimetrics.datasetsetting.DatasetRow;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.List;

/**
 * Writes dataset rows to CSV files with the correct header for the selected granularity.
 */
public final class CSVWriter {

    /**
     * Builds the CSV header line for the selected granularity.
     *
     * @return comma-separated header line
     */
    private static String header() {
        return String.join(",", buildColumns("Class"));
    }

    /**
     * Builds the ordered CSV column names shared by the exported datasets.
     *
     * @param entityColumn label of the granularity-specific entity column
     * @return ordered column names
     */
    private static String[] buildColumns(String entityColumn) {
        return new String[] {
                "Project", "Path", entityColumn, "ReleaseId",
                "LOC", "WMC", "LCOM", "NSmells", "NSmellsDensity",
                "NR", "NFix", "NAuth", "LOC_Added", "LOC_Deleted", "Churn",
                "totalNR", "totalNFix", "totalNAuth", "totalChurn", "Age",
                "maxLOC", "maxWMC", "maxNSmells",
                "PriorityMax", "PriorityAvg", "TypeRiskMax", "TypeRiskAvg",
                "ComponentCountMax", "ComponentCountAvg", "OpenTickets",
                "TLCC_Lin", "TLCC_Log",
                "prevNSmells", "prevBuggy", "Buggy"
        };
    }

    /**
     * Opens a CSV file for appending after rewriting its header.
     *
     * @param file output CSV file path
     * @return buffered writer positioned after the header line
     * @throws CSVException when the file cannot be initialized
     */
    public BufferedWriter open(Path file) throws CSVException {
        try {
            Files.createDirectories(file.getParent());

            // header + truncate
            try (BufferedWriter headerWriter = Files.newBufferedWriter(
                    file, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING)) {
                headerWriter.write(header());
                headerWriter.newLine();
            }

            // writer in append per le righe
            return Files.newBufferedWriter(
                    file, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);

        } catch (IOException e) {
            throw new CSVException("Cannot open " + file, e);
        }
    }

    /**
     * Appends the provided dataset rows to an already opened CSV writer.
     *
     * @param w buffered writer opened by {@link #open(Path)}
     * @param rows dataset rows to serialize
     * @throws CSVException when writing or flushing fails
     */
    public void append(BufferedWriter w, List<? extends DatasetRow> rows) throws CSVException {
        try {
            for (DatasetRow r : rows) {
                w.write(r.toCsvLine());
                w.newLine();
            }
            w.flush();
        } catch (Exception e) {
            throw new CSVException("CSV write failed", e);
        }
    }
}
