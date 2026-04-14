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
