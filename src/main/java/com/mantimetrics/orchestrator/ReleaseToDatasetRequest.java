package com.mantimetrics.orchestrator;

import com.mantimetrics.git.GitReleaseSnapshot;
import com.mantimetrics.history.StoreReleaseInMemory;
import com.mantimetrics.labeling.ReleaseLabeling;
import com.mantimetrics.datasetSetting.DatasetRow;
import com.mantimetrics.javaParsing.ScanResult;

import java.util.Map;

/**
 * Narrow request object passed to the dataset collector so the release pipeline stays explicit and testable.
 *
 * @param releaseSources extracted source tree for the release
 * @param repo repository name
 * @param tag release tag currently being analyzed
 * @param commitData commit and churn information for the current release range
 * @param previousRows previous dataset rows for the same granularity, keyed by unique identifier
 * @param historyStore cumulative history state for the same granularity
 * @param labelIndex historical bug labels used to mark buggy rows
 * @param sonarSmellsByFile SonarCloud file-level code-smell counts; empty map when SonarCloud is unconfigured
 * @param excludeChurnZero whether to drop rows whose current-release churn is zero
 */
public record ReleaseToDatasetRequest(
        ScanResult releaseSources,
        String repo,
        String tag,
        GitReleaseSnapshot commitData,
        Map<String, DatasetRow> previousRows,
        StoreReleaseInMemory historyStore,
        ReleaseLabeling labelIndex,
        Map<String, Integer> sonarSmellsByFile,
        boolean excludeChurnZero
) {
}
