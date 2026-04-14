package com.mantimetrics.analysis;

import com.mantimetrics.csv.CSVWriter;
import com.mantimetrics.history.RowHistoryStore;
import com.mantimetrics.labeling.HistoricalBugLabelIndex;
import com.mantimetrics.model.DatasetRow;
import com.mantimetrics.pmd.PmdAnalyzer;

import java.io.BufferedWriter;
import java.util.Map;

public record ProjectContext(
        String owner,
        String repo,
        Granularity granularity,
        PmdAnalyzer pmd,
        CSVWriter csvOut,
        Map<String, DatasetRow> prevData,
        RowHistoryStore historyStore,
        HistoricalBugLabelIndex labelIndex,
        BufferedWriter writer
) {
}
