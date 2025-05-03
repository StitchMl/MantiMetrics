package com.mantimetrics.csv;

import com.mantimetrics.model.MethodData;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

public class CSVWriter {
    public void write(String filePath, List<MethodData> data) throws IOException {
        try (FileWriter writer = new FileWriter(filePath)) {
            writer.append("Project,Method,ReleaseID,LOC,StmtCount,Cyclomatic,Cognitive,Buggy\n");
            for (MethodData md : data) {
                writer.append(md.toCsvLine());
                writer.append("\n");
            }
        }
    }
}