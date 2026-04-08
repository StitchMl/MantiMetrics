package com.mantimetrics.analysis;

import com.mantimetrics.csv.CSVWriter;
import com.mantimetrics.git.GitService;
import com.mantimetrics.model.DatasetRow;
import com.mantimetrics.pmd.PmdAnalyzer;

import java.io.BufferedWriter;
import java.util.List;
import java.util.Map;

public record ProjectContext(
        String owner,
        String repo,
        Granularity granularity,
        GitService git,
        PmdAnalyzer pmd,
        CSVWriter csvOut,
        Map<String, DatasetRow> prevData,
        List<String> bugKeys,
        BufferedWriter writer
) {
}
