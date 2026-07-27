package com.mantimetrics.orchestrator;

import com.mantimetrics.history.ComulationMetricsCalculator;
import com.mantimetrics.feature.ClassMetrics;
import com.mantimetrics.datasetSetting.DatasetClassData;
import com.mantimetrics.datasetSetting.DatasetRow;
import com.mantimetrics.utility.PathUtility;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

/**
 * Applies release-local Git history, cumulative history and historical bug labels to class rows.
 */
final class DatasetRowEnricher {

    /**
     * Enriches class-level rows with release-local and cumulative history features.
     *
     * @param rows parsed class rows for the current release
     * @param request immutable release request carrying history and labels
     * @return enriched class rows ready for serialization
     */
    List<DatasetClassData> enrichClasses(
            List<DatasetClassData> rows,
            ReleaseToDatasetRequest request
    ) {
        List<DatasetClassData> result = new ArrayList<>();
        for (DatasetClassData row : rows) {
            String relativePath = normalizedPath(row);
            List<String> commits = request.commitData().touchesFor(relativePath);
            int currentCodeSmells = codeSmellsForRow(row, request.sonarSmellsByFile());
            ComulationMetricsCalculator historyState = updateHistory(
                    row.getUniqueKey(), relativePath, request, row.getMetrics(), currentCodeSmells);
            DatasetClassData previous =
                    request.previousRows().get(row.getUniqueKey()) instanceof DatasetClassData type ? type : null;
            result.add(row.toBuilder()
                    .commitHashes(commits)
                    .codeSmells(currentCodeSmells)
                    .issueTouches(request.commitData().issueTouchesFor(relativePath).size())
                    .totalIssueTouches(historyState.totalIssueTouches())
                    .touches(commits.size())
                    .totalTouches(historyState.totalTouches())
                    .authors(distinctCount(request.commitData().authorsFor(relativePath)))
                    .totalAuthors(historyState.totalAuthors())
                    .addedLines(request.commitData().additionsFor(relativePath))
                    .deletedLines(request.commitData().deletionsFor(relativePath))
                    .churn(request.commitData().churnFor(relativePath))
                    .totalChurn(historyState.totalChurn())
                    .prevCodeSmells(previous != null ? previous.getCodeSmells() : 0)
                    .ageInReleases(historyState.ageInReleases())
                    .buggy(isBuggyRow(request.tag(), relativePath, request))
                    .prevBuggy(previous != null && previous.isBuggy())
                    .maxLoc(historyState.maxLoc())
                    .maxWmc(historyState.maxWmc())
                    .maxNSmells(historyState.maxNSmells())
                    .build());
        }
        return result;
    }

    /**
     * Updates the cumulative history state for a class row and returns the refreshed value.
     *
     * @param uniqueKey stable dataset identifier for the row
     * @param relativePath normalized relative source path
     * @param request immutable release request carrying commit history and the mutable store
     * @param metrics current-release class metrics
     * @param currentCodeSmells NSmells count for the current release
     * @return updated history state after processing the current release
     */
    private ComulationMetricsCalculator updateHistory(
            String uniqueKey,
            String relativePath,
            ReleaseToDatasetRequest request,
            ClassMetrics metrics,
            int currentCodeSmells
    ) {
        ComulationMetricsCalculator previous = request.historyStore().get(uniqueKey);
        List<String> totalAuthors = mergeAuthors(previous, distinctAuthors(request.commitData().authorsFor(relativePath)));
        ComulationMetricsCalculator updated = new ComulationMetricsCalculator(
                prevInt(previous, ComulationMetricsCalculator::totalTouches) + request.commitData().touchesFor(relativePath).size(),
                prevInt(previous, ComulationMetricsCalculator::totalIssueTouches) + request.commitData().issueTouchesFor(relativePath).size(),
                prevInt(previous, ComulationMetricsCalculator::totalChurn) + request.commitData().churnFor(relativePath),
                totalAuthors,
                prevInt(previous, ComulationMetricsCalculator::ageInReleases) + 1,
                Math.max(prevInt(previous, ComulationMetricsCalculator::maxLoc), metrics.getLoc()),
                Math.max(prevInt(previous, ComulationMetricsCalculator::maxWmc), metrics.getWmc()),
                Math.max(prevInt(previous, ComulationMetricsCalculator::maxNSmells), currentCodeSmells)
        );
        request.historyStore().put(uniqueKey, updated);
        return updated;
    }

    /** Merges the previous author list with the current release authors, preserving order. */
    private List<String> mergeAuthors(ComulationMetricsCalculator previous, List<String> currentAuthors) {
        List<String> totalAuthors = new ArrayList<>();
        if (previous != null) {
            totalAuthors.addAll(previous.authors());
        }
        currentAuthors.stream()
                .filter(author -> !totalAuthors.contains(author))
                .forEach(totalAuthors::add);
        return totalAuthors;
    }

    /** Returns {@code fn(prev)} when {@code prev} is non-null, otherwise {@code 0}. */
    private static int prevInt(ComulationMetricsCalculator prev, ToIntFunction<ComulationMetricsCalculator> fn) {
        return prev != null ? fn.applyAsInt(prev) : 0;
    }

    /**
     * Returns the SonarCloud code-smell (NSmells) count for a row, or 0 when absent from the index.
     *
     * @param row dataset row being enriched
     * @param sonarSmells SonarCloud file-level smell counts keyed by normalized path
     * @return NSmells count for the row
     */
    private int codeSmellsForRow(DatasetRow row, java.util.Map<String, Integer> sonarSmells) {
        return sonarSmells.getOrDefault(normalizedPath(row), 0);
    }

    /**
     * Reports whether the current row is historically labeled as buggy for the analyzed release.
     *
     * @param releaseId release identifier currently being processed
     * @param relativePath normalized relative source path
     * @param request immutable release request carrying the historical label index
     * @return {@code true} when the row belongs to a historically buggy file in the current release
     */
    private boolean isBuggyRow(String releaseId, String relativePath, ReleaseToDatasetRequest request) {
        return request.labelIndex().isBuggy(releaseId, relativePath);
    }

    /** Normalizes the row path into the canonical dataset representation. */
    private String normalizedPath(DatasetRow row) {
        return PathUtility.normalizeDatasetPath(row.getPath());
    }

    /** Removes duplicate authors while preserving encounter order. */
    private List<String> distinctAuthors(List<String> authors) {
        return authors.stream().distinct().toList();
    }

    /** Counts the distinct authors touching a row. */
    private int distinctCount(List<String> authors) {
        return distinctAuthors(authors).size();
    }
}
