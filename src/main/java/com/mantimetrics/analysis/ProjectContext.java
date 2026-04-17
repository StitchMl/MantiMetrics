package com.mantimetrics.analysis;

import com.mantimetrics.csv.CSVWriter;
import com.mantimetrics.history.RowHistoryStore;
import com.mantimetrics.labeling.HistoricalBugLabelIndex;
import com.mantimetrics.model.DatasetRow;
import com.mantimetrics.pmd.PmdAnalyzer;

import java.io.BufferedWriter;
import java.util.Map;

/**
 * Mutable execution context associated with one dataset granularity while a project is being processed.
 *
 * @param owner repository owner
 * @param repo repository name
 * @param granularity dataset granularity handled by this context
 * @param pmd PMD analyzer reused across releases
 * @param csvOut CSV writer service used to append rows
 * @param prevData rows produced for the previous release, keyed by dataset identifier
 * @param historyStore cumulative history state shared across releases for this granularity
 * @param labelIndex historical bug labels available for the project timeline
 * @param writer buffered writer bound to the output CSV file
 */
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
