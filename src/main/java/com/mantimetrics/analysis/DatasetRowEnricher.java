package com.mantimetrics.analysis;

import com.mantimetrics.history.RowHistoryState;
import com.mantimetrics.metrics.MethodMetrics;
import com.mantimetrics.model.ClassData;
import com.mantimetrics.model.DatasetRow;
import com.mantimetrics.model.MethodData;
import com.mantimetrics.util.AnalysisPathUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Applies release-local Git history, cumulative history and historical bug labels to parsed rows.
 */
final class DatasetRowEnricher {
    /**
     * Enriches method-level rows with release-local and cumulative history features.
     *
     * @param rows parsed method rows for the current release
     * @param request immutable release request carrying history and labels
     * @param violationIndex PMD violations indexed by relative path
     * @return enriched method rows ready for serialization
     */
    List<MethodData> enrichMethods(
            List<MethodData> rows,
            ReleaseDatasetRequest request,
            ReleaseViolationIndex violationIndex
    ) {
        List<MethodData> result = new ArrayList<>();
        for (MethodData row : rows) {
            String relativePath = normalizedPath(row);
            List<String> commits = request.commitData().touchesFor(relativePath);
            int currentCodeSmells = codeSmellsForRow(row, violationIndex);
            RowHistoryState historyState = updateHistory(
                    row.getUniqueKey(), relativePath, request, row.getMetrics(), currentCodeSmells);
            MethodData previous = request.previousRows().get(row.getUniqueKey()) instanceof MethodData method ? method : null;
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
                    .maxCyclomatic(historyState.maxCyclomatic())
                    .maxCognitive(historyState.maxCognitive())
                    .maxNSmells(historyState.maxNSmells())
                    .build());
        }
        return result;
    }

    /**
     * Enriches class-level rows with release-local and cumulative history features.
     *
     * @param rows parsed class rows for the current release
     * @param request immutable release request carrying history and labels
     * @param violationIndex PMD violations indexed by relative path
     * @return enriched class rows ready for serialization
     */
    List<ClassData> enrichClasses(
            List<ClassData> rows,
            ReleaseDatasetRequest request,
            ReleaseViolationIndex violationIndex
    ) {
        List<ClassData> result = new ArrayList<>();
        for (ClassData row : rows) {
            String relativePath = normalizedPath(row);
            List<String> commits = request.commitData().touchesFor(relativePath);
            int currentCodeSmells = codeSmellsForRow(row, violationIndex);
            RowHistoryState historyState = updateHistory(
                    row.getUniqueKey(), relativePath, request, row.getMetrics(), currentCodeSmells);
            ClassData previous = request.previousRows().get(row.getUniqueKey()) instanceof ClassData type ? type : null;
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
                    .maxCyclomatic(historyState.maxCyclomatic())
                    .maxCognitive(historyState.maxCognitive())
                    .maxNSmells(historyState.maxNSmells())
                    .build());
        }
        return result;
    }

    /**
     * Updates the cumulative history state associated with a dataset row and returns the refreshed value.
     * Computes the current nSmells internally from the provided metrics and codeSmells count.
     *
     * @param uniqueKey stable dataset identifier for the row
     * @param relativePath normalized relative source path
     * @param request immutable release request carrying commit history and the mutable store
     * @param metrics current-release static metrics for the entity
     * @param currentCodeSmells PMD violation count for the entity in the current release
     * @return updated history state after processing the current release
     */
    private RowHistoryState updateHistory(
            String uniqueKey,
            String relativePath,
            ReleaseDatasetRequest request,
            MethodMetrics metrics,
            int currentCodeSmells
    ) {
        RowHistoryState previous = request.historyStore().get(uniqueKey);
        List<String> currentAuthors = distinctAuthors(request.commitData().authorsFor(relativePath));
        List<String> totalAuthors = new ArrayList<>();
        if (previous != null) {
            totalAuthors.addAll(previous.authors());
        }
        currentAuthors.stream()
                .filter(author -> !totalAuthors.contains(author))
                .forEach(totalAuthors::add);

        int binarySmells = (metrics.isLongMethod() ? 1 : 0)
                + (metrics.isGodClass() ? 1 : 0)
                + (metrics.isFeatureEnvy() ? 1 : 0)
                + (metrics.isDuplicatedCode() ? 1 : 0);
        int currentNSmells = currentCodeSmells + binarySmells;

        RowHistoryState updated = new RowHistoryState(
                (previous != null ? previous.totalTouches() : 0) + request.commitData().touchesFor(relativePath).size(),
                (previous != null ? previous.totalIssueTouches() : 0) + request.commitData().issueTouchesFor(relativePath).size(),
                (previous != null ? previous.totalChurn() : 0) + request.commitData().churnFor(relativePath),
                totalAuthors,
                previous != null ? previous.ageInReleases() + 1 : 1,
                Math.max(previous != null ? previous.maxLoc() : 0, metrics.getLoc()),
                Math.max(previous != null ? previous.maxCyclomatic() : 0, metrics.getCyclomatic()),
                Math.max(previous != null ? previous.maxCognitive() : 0, metrics.getCognitive()),
                Math.max(previous != null ? previous.maxNSmells() : 0, currentNSmells)
        );
        request.historyStore().put(uniqueKey, updated);
        return updated;
    }

    /**
     * Counts the PMD violations that fall inside the row source range.
     *
     * @param row dataset row being enriched
     * @param violationIndex indexed PMD violations for the current release
     * @return number of violations associated with the row range
     */
    private int codeSmellsForRow(DatasetRow row, ReleaseViolationIndex violationIndex) {
        return violationIndex.countViolations(normalizedPath(row), row.getStartLine(), row.getEndLine());
    }

    /**
     * Reports whether the current row is historically labeled as buggy for the analyzed release.
     *
     * @param releaseId release identifier currently being processed
     * @param relativePath normalized relative source path
     * @param request immutable release request carrying the historical label index
     * @return {@code true} when the row belongs to a historically buggy file in the current release
     */
    private boolean isBuggyRow(String releaseId, String relativePath, ReleaseDatasetRequest request) {
        return request.labelIndex().isBuggy(releaseId, relativePath);
    }

    /**
     * Normalizes the row path into the canonical dataset representation.
     *
     * @param row dataset row whose path must be normalized
     * @return normalized relative path
     */
    private String normalizedPath(DatasetRow row) {
        return AnalysisPathUtils.normalizeDatasetPath(row.getPath());
    }

    /**
     * Removes duplicate authors while preserving their encounter order.
     *
     * @param authors authors collected from the current release history
     * @return distinct author list
     */
    private List<String> distinctAuthors(List<String> authors) {
        return authors.stream().distinct().collect(Collectors.toList());
    }

    /**
     * Counts the distinct authors touching a row.
     *
     * @param authors authors collected from the current release history
     * @return number of unique authors
     */
    private int distinctCount(List<String> authors) {
        return distinctAuthors(authors).size();
    }
}
