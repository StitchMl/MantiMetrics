package com.mantimetrics.analysis;

import com.mantimetrics.git.ReleaseCommitData;
import com.mantimetrics.history.RowHistoryStore;
import com.mantimetrics.labeling.HistoricalBugLabelIndex;
import com.mantimetrics.model.DatasetRow;
import com.mantimetrics.parser.SourceScanResult;
import net.sourceforge.pmd.reporting.RuleViolation;

import java.util.List;
import java.util.Map;

/**
 * Narrow request object passed to the dataset collector so the release pipeline stays explicit and testable.
 *
 * @param releaseSources extracted source tree for the release
 * @param repo repository name
 * @param tag release tag currently being analyzed
 * @param cloneCacheKey key of the clone map prepared for the release
 * @param commitData commit and churn information for the current release range
 * @param previousRows previous dataset rows for the same granularity, keyed by unique identifier
 * @param historyStore cumulative history state for the same granularity
 * @param violations PMD rule violations found in the release
 * @param labelIndex historical bug labels used to mark buggy rows
 */
public record ReleaseDatasetRequest(
        SourceScanResult releaseSources,
        String repo,
        String tag,
        String cloneCacheKey,
        ReleaseCommitData commitData,
        Map<String, DatasetRow> previousRows,
        RowHistoryStore historyStore,
        List<RuleViolation> violations,
        HistoricalBugLabelIndex labelIndex
) {
}
