package com.mantimetrics.csv;

import com.mantimetrics.Granularity;
import com.mantimetrics.model.DatasetRow;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.List;

public final class CSVWriter {

    /** exception thrown when CSV write fails */
    private static final String[] METHOD_COLUMNS = {
            "Project","Path","Method","ReleaseId",
            "LOC","StmtCount","Cyclomatic","Cognitive",
            "DistinctOperators","DistinctOperands",
            "TotalOperators","TotalOperands",
            "Vocabulary","Length","Volume","Difficulty","Effort",
            "MaxNestingDepth","isLongMethod","isGodClass","isFeatureEnvy",
            "isDuplicatedCode","CodeSmells","Touches","prevCodeSmells",
            "prevBuggy","Buggy"
    };

    private static final String[] CLASS_COLUMNS = {
            "Project","Path","Class","ReleaseId",
            "LOC","StmtCount","Cyclomatic","Cognitive",
            "DistinctOperators","DistinctOperands",
            "TotalOperators","TotalOperands",
            "Vocabulary","Length","Volume","Difficulty","Effort",
            "MaxNestingDepth","isLongMethod","isGodClass","isFeatureEnvy",
            "isDuplicatedCode","CodeSmells","Touches","prevCodeSmells",
            "prevBuggy","Buggy"
    };

    private static String headerFor(Granularity g) {
        return String.join(",", g == Granularity.CLASS ? CLASS_COLUMNS : METHOD_COLUMNS);
    }

    /** opens the file in appending; if it does not exist, it first writes the header */
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

    /** writes (already in appending) the received list of rows */
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