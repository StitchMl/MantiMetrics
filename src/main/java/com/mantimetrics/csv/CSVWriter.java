package com.mantimetrics.csv;

import com.mantimetrics.model.MethodData;

import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.List;

public final class CSVWriter {

    private static final String HEADER = String.join(",",
            "Project","Path","Method","ReleaseId","VersionId","CommitId",
            "LOC","StmtCount","Cyclomatic","Cognitive",
            "DistinctOperators","DistinctOperands",
            "TotalOperators","TotalOperands",
            "Vocabulary","Length","Volume","Difficulty","Effort",
            "MaxNestingDepth","isLongMethod","isGodClass","isFeatureEnvy",
            "isDuplicatedCode","Buggy");

    /** opens the file in appending; if it does not exist, it first writes the header */
    public BufferedWriter open(Path file) throws CsvWriteException {
        try {
            Files.createDirectories(file.getParent());

            boolean writeHeader = Files.notExists(file);
            BufferedWriter w = Files.newBufferedWriter(
                    file,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND);

            if (writeHeader) {
                w.write(HEADER);
                w.write('\n');
            }
            return w;
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