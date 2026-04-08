package com.mantimetrics.analysis;

import com.mantimetrics.jira.JiraClient;
import com.mantimetrics.model.ClassData;
import com.mantimetrics.model.DatasetRow;
import com.mantimetrics.model.MethodData;
import com.mantimetrics.util.AnalysisPathUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class DatasetRowEnricher {
    private final JiraClient jiraClient;

    DatasetRowEnricher(JiraClient jiraClient) {
        this.jiraClient = jiraClient;
    }

    List<MethodData> enrichMethods(
            List<MethodData> rows,
            Map<String, List<String>> touchMap,
            Map<String, List<String>> fileToKeys,
            Map<String, DatasetRow> previousRows,
            ReleaseViolationIndex violationIndex,
            List<String> bugKeys
    ) {
        List<MethodData> result = new ArrayList<>();
        for (MethodData row : rows) {
            List<String> commits = commitsForRow(row, touchMap);
            if (commits.isEmpty()) {
                continue;
            }

            MethodData previous = previousRows.get(row.getUniqueKey()) instanceof MethodData method ? method : null;
            result.add(row.toBuilder()
                    .commitHashes(commits)
                    .codeSmells(codeSmellsForRow(row, violationIndex))
                    .touches(commits.size())
                    .buggy(isBuggyRow(row, fileToKeys, bugKeys))
                    .prevCodeSmells(previous != null ? previous.getCodeSmells() : 0)
                    .prevBuggy(previous != null && previous.isBuggy())
                    .build());
        }
        return result;
    }

    List<ClassData> enrichClasses(
            List<ClassData> rows,
            Map<String, List<String>> touchMap,
            Map<String, List<String>> fileToKeys,
            Map<String, DatasetRow> previousRows,
            ReleaseViolationIndex violationIndex,
            List<String> bugKeys
    ) {
        List<ClassData> result = new ArrayList<>();
        for (ClassData row : rows) {
            List<String> commits = commitsForRow(row, touchMap);
            if (commits.isEmpty()) {
                continue;
            }

            ClassData previous = previousRows.get(row.getUniqueKey()) instanceof ClassData type ? type : null;
            result.add(row.toBuilder()
                    .commitHashes(commits)
                    .codeSmells(codeSmellsForRow(row, violationIndex))
                    .touches(commits.size())
                    .buggy(isBuggyRow(row, fileToKeys, bugKeys))
                    .prevCodeSmells(previous != null ? previous.getCodeSmells() : 0)
                    .prevBuggy(previous != null && previous.isBuggy())
                    .build());
        }
        return result;
    }

    private List<String> commitsForRow(DatasetRow row, Map<String, List<String>> touchMap) {
        String relativePath = AnalysisPathUtils.normalizeDatasetPath(row.getPath());
        return touchMap.getOrDefault(relativePath, List.of());
    }

    private int codeSmellsForRow(DatasetRow row, ReleaseViolationIndex violationIndex) {
        String relativePath = AnalysisPathUtils.normalizeDatasetPath(row.getPath());
        return violationIndex.countViolations(relativePath, row.getStartLine(), row.getEndLine());
    }

    private boolean isBuggyRow(DatasetRow row, Map<String, List<String>> fileToKeys, List<String> bugKeys) {
        String relativePath = AnalysisPathUtils.normalizeDatasetPath(row.getPath());
        return jiraClient.isMethodBuggy(fileToKeys.getOrDefault(relativePath, List.of()), bugKeys);
    }
}
