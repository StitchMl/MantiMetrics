package com.mantimetrics.csv;

import com.mantimetrics.model.MethodData;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.List;

public final class CSVWriter {

    /** exception thrown when CSV write fails */
    private static final String[] COLUMNS = {
            "Project","Path","Method","ReleaseId",
            "LOC","StmtCount","Cyclomatic","Cognitive",
            "DistinctOperators","DistinctOperands",
            "TotalOperators","TotalOperands",
            "Vocabulary","Length","Volume","Difficulty","Effort",
            "MaxNestingDepth","isLongMethod","isGodClass","isFeatureEnvy",
            "isDuplicatedCode","CodeSmells","Touches","prevCodeSmells",
            "prevBuggy","Buggy"
    };

    private static final String HEADER = String.join(",", COLUMNS);

    /** opens the file in appending; if it does not exist, it first writes the header */
    public BufferedWriter open(Path file) throws CsvWriteException {
        try {
            Files.createDirectories(file.getParent());
            if (Files.exists(file)) {
                // Writing headers in a temporary BufferedWriter
                try (BufferedWriter headerWriter = Files.newBufferedWriter(
                        file, StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.TRUNCATE_EXISTING)) {
                    headerWriter.write(HEADER);
                    headerWriter.newLine();
                }
            }
            // I open the writer to be returned, without closing it immediately
            return Files.newBufferedWriter(
                    file, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new CsvWriteException("Cannot open " + file, e);
        }
    }

    /** writes (already in appending) the received list of rows */
    public void append(BufferedWriter w, List<MethodData> rows) throws CsvWriteException {
        try {
            for (MethodData m : rows) {
                w.write(m.toCsvLine());
                w.newLine();
            }
            w.flush();
        } catch (Exception e) {
            throw new CsvWriteException("CSV write failed", e);
        }
    }
}