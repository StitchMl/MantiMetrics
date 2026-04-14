package com.mantimetrics.git;

import java.util.List;
import java.util.Map;

public record ReleaseCommitData(
        Map<String, List<String>> touchMap,
        Map<String, List<String>> issueTouchMap,
        Map<String, List<String>> fileToIssueKeys,
        Map<String, List<String>> authorMap,
        Map<String, Integer> additionsMap,
        Map<String, Integer> deletionsMap
) {
    public List<String> touchesFor(String relativePath) {
        return touchMap.getOrDefault(relativePath, List.of());
    }

    public List<String> issueTouchesFor(String relativePath) {
        return issueTouchMap.getOrDefault(relativePath, List.of());
    }

    @SuppressWarnings("unused")
    public List<String> issueKeysFor(String relativePath) {
        return fileToIssueKeys.getOrDefault(relativePath, List.of());
    }

    public List<String> authorsFor(String relativePath) {
        return authorMap.getOrDefault(relativePath, List.of());
    }

    public int additionsFor(String relativePath) {
        return additionsMap.getOrDefault(relativePath, 0);
    }

    public int deletionsFor(String relativePath) {
        return deletionsMap.getOrDefault(relativePath, 0);
    }

    public int churnFor(String relativePath) {
        return additionsFor(relativePath) + deletionsFor(relativePath);
    }
}
