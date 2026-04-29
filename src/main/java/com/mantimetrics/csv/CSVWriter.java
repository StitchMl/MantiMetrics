package com.mantimetrics.csv;

import com.mantimetrics.analysis.Granularity;
import com.mantimetrics.model.DatasetRow;

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
     * @param g target dataset granularity
     * @return comma-separated header line
     */
    private static String headerFor(Granularity g) {
        return String.join(",", buildColumns(g == Granularity.CLASS ? "Class" : "Method"));
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
                "LOC", "StmtCount", "Cyclomatic", "Cognitive",
                "DistinctOperators", "DistinctOperands",
                "TotalOperators", "TotalOperands",
                "Vocabulary", "Length", "Volume", "Difficulty", "Effort",
                "MaxNestingDepth", "isLongMethod", "isGodClass", "isFeatureEnvy",
                "isDuplicatedCode", "CodeSmells", "NSmells", "SmellDensity",
                "maxLOC", "maxCyclomatic", "maxCognitive", "maxNSmells",
                "Touches", "TotalTouches",
                "IssueTouches", "TotalIssueTouches", "Authors", "TotalAuthors",
                "AddedLines", "DeletedLines", "Churn", "TotalChurn", "prevCodeSmells", "AgeInReleases",
                "prevBuggy", "Buggy"
        };
    }

    /**
     * Opens a CSV file for appending after rewriting its header.
     *
     * @param file output CSV file path
     * @param granularity dataset granularity whose header must be written
     * @return buffered writer positioned after the header line
     * @throws CsvWriteException when the file cannot be initialized
     */
    public BufferedWriter open(Path file, Granularity granularity) throws CsvWriteException {
        try {
            Files.createDirectories(file.getParent());

            // header + truncate
            try (BufferedWriter headerWriter = Files.newBufferedWriter(
                    file, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING)) {
                headerWriter.write(headerFor(granularity));
                headerWriter.newLine();
            }

            // writer in append per le righe
            return Files.newBufferedWriter(
                    file, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);

        } catch (IOException e) {
            throw new CsvWriteException("Cannot open " + file, e);
        }
    }

    /**
     * Appends the provided dataset rows to an already opened CSV writer.
     *
     * @param w buffered writer opened by {@link #open(Path, Granularity)}
     * @param rows dataset rows to serialize
     * @throws CsvWriteException when writing or flushing fails
     */
    public void append(BufferedWriter w, List<? extends DatasetRow> rows) throws CsvWriteException {
        try {
            for (DatasetRow r : rows) {
                w.write(r.toCsvLine());
                w.newLine();
            }
            w.flush();
        } catch (Exception e) {
            throw new CsvWriteException("CSV write failed", e);
        }
    }
}
