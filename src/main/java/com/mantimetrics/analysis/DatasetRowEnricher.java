package com.mantimetrics.analysis;

import com.mantimetrics.history.RowHistoryState;
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
    List<MethodData> enrichMethods(
            List<MethodData> rows,
            ReleaseDatasetRequest request,
            ReleaseViolationIndex violationIndex
    ) {
        List<MethodData> result = new ArrayList<>();
        for (MethodData row : rows) {
            String relativePath = normalizedPath(row);
            List<String> commits = request.commitData().touchesFor(relativePath);
            RowHistoryState historyState = updateHistory(row.getUniqueKey(), relativePath, request);
            MethodData previous = request.previousRows().get(row.getUniqueKey()) instanceof MethodData method ? method : null;
            result.add(row.toBuilder()
                    .commitHashes(commits)
                    .codeSmells(codeSmellsForRow(row, violationIndex))
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
                    .build());
        }
        return result;
    }

    List<ClassData> enrichClasses(
            List<ClassData> rows,
            ReleaseDatasetRequest request,
            ReleaseViolationIndex violationIndex
    ) {
        List<ClassData> result = new ArrayList<>();
        for (ClassData row : rows) {
            String relativePath = normalizedPath(row);
            List<String> commits = request.commitData().touchesFor(relativePath);
            RowHistoryState historyState = updateHistory(row.getUniqueKey(), relativePath, request);
            ClassData previous = request.previousRows().get(row.getUniqueKey()) instanceof ClassData type ? type : null;
            result.add(row.toBuilder()
                    .commitHashes(commits)
                    .codeSmells(codeSmellsForRow(row, violationIndex))
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
                    .build());
        }
        return result;
    }

    private RowHistoryState updateHistory(String uniqueKey, String relativePath, ReleaseDatasetRequest request) {
        RowHistoryState previous = request.historyStore().get(uniqueKey);
        List<String> currentAuthors = distinctAuthors(request.commitData().authorsFor(relativePath));
        List<String> totalAuthors = new ArrayList<>();
        if (previous != null) {
            totalAuthors.addAll(previous.authors());
        }
        currentAuthors.stream()
                .filter(author -> !totalAuthors.contains(author))
                .forEach(totalAuthors::add);

        RowHistoryState updated = new RowHistoryState(
                (previous != null ? previous.totalTouches() : 0) + request.commitData().touchesFor(relativePath).size(),
                (previous != null ? previous.totalIssueTouches() : 0) + request.commitData().issueTouchesFor(relativePath).size(),
                (previous != null ? previous.totalChurn() : 0) + request.commitData().churnFor(relativePath),
                totalAuthors,
                previous != null ? previous.ageInReleases() + 1 : 1
        );
        request.historyStore().put(uniqueKey, updated);
        return updated;
    }

    private int codeSmellsForRow(DatasetRow row, ReleaseViolationIndex violationIndex) {
        return violationIndex.countViolations(normalizedPath(row), row.getStartLine(), row.getEndLine());
    }

    private boolean isBuggyRow(String releaseId, String relativePath, ReleaseDatasetRequest request) {
        return request.labelIndex().isBuggy(releaseId, relativePath);
    }

    private String normalizedPath(DatasetRow row) {
        return AnalysisPathUtils.normalizeDatasetPath(row.getPath());
    }

    private List<String> distinctAuthors(List<String> authors) {
        return authors.stream().distinct().collect(Collectors.toList());
    }

    private int distinctCount(List<String> authors) {
        return distinctAuthors(authors).size();
    }
}
