package com.mantimetrics.csv;

import com.mantimetrics.model.MethodData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.List;

public class CSVWriter {
    private static final Logger log = LoggerFactory.getLogger(CSVWriter.class);

    private static final String HEADER = String.join(",",
            "Project","Path","Method","ReleaseId","VersionId","CommitId",
            "LOC","StmtCount","Cyclomatic","Cognitive",
            "DistinctOperators","DistinctOperands",
            "TotalOperators","TotalOperands",
            "Vocabulary","Length","Volume","Difficulty","Effort",
            "MaxNestingDepth","isLongMethod","isGodClass","isFeatureEnvy",
            "isDuplicatedCode","Buggy");

    public void write(Path file, List<MethodData> rows) throws Exception {
        try (BufferedWriter w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            w.write(HEADER); w.write('\n');
            for (MethodData m : rows) {
                w.write(escape(m.toCsvLine())); w.write('\n');
            }
        }
        log.info("CSV [{}] written – {} rows", file.getFileName(), rows.size());
    }

    private static String escape(String s) {
        return s.indexOf('"') >= 0 || s.indexOf(',') >= 0 || s.indexOf('\n') >= 0
                ? '"' + s.replace("\"", "\"\"") + '"'
                : s;
    }
}