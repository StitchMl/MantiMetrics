package com.mantimetrics.csv;

import com.mantimetrics.model.MethodData;

import java.io.BufferedWriter;
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
            "isDuplicatedCode","CodeSmells","Buggy"
    };

    private static final String HEADER = String.join(",", COLUMNS);

    /** opens the file in appending; if it does not exist, it first writes the header */
    public BufferedWriter open(Path file) throws CsvWriteException {
        try {
            Files.createDirectories(file.getParent());
            boolean writeHeader = Files.notExists(file);

            // 1) Write header (stream closed immediately afterward)
            if (writeHeader) {
                try (BufferedWriter headerWriter = Files.newBufferedWriter(
                        file, StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE)) {
                    headerWriter.write(HEADER);
                    headerWriter.newLine();
                }
            }

            // 2) Opening writer for the appendix without automatic closure
            return Files.newBufferedWriter(
                    file, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception e) {
            throw new CsvWriteException("Cannot open " + file, e);
        }
    }

    /** writes (already in appending) the received list of rows */
    public void append(BufferedWriter w, List<MethodData> rows) throws CsvWriteException {
        try {
            for (MethodData m : rows) {
                w.write(m.toCsvLine());
                w.write('\n');
            }
            w.flush();
        } catch (Exception e) {
            throw new CsvWriteException("CSV write failed", e);
        }
    }
}