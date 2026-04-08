package com.mantimetrics.git;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

final class ReleaseCommitDataBuilder {

    static ReleaseCommitData aggregate(List<ReleaseCommitSnapshot> commits) {
        Map<String, List<String>> touchMap = new HashMap<>();
        Map<String, List<String>> fileToIssueKeys = new HashMap<>();

        for (ReleaseCommitSnapshot commit : commits) {
            Set<String> javaFiles = commit.files().stream()
                    .filter(path -> path.endsWith(".java"))
                    .collect(Collectors.toCollection(LinkedHashSet::new));

            if (javaFiles.isEmpty()) {
                continue;
            }

            for (String file : javaFiles) {
                touchMap.computeIfAbsent(file, ignored -> new ArrayList<>()).add(commit.sha());
            }

            IssueKeySupport.addKeysForFiles(javaFiles, IssueKeySupport.extractKeys(commit.message()), fileToIssueKeys);
        }

        return new ReleaseCommitData(immutableCopy(touchMap), immutableCopy(fileToIssueKeys));
    }

    private static Map<String, List<String>> immutableCopy(Map<String, List<String>> source) {
        Map<String, List<String>> copy = new HashMap<>();
        source.forEach((key, value) -> copy.put(key, List.copyOf(value)));
        return Collections.unmodifiableMap(copy);
    }

    record ReleaseCommitSnapshot(String sha, String message, Set<String> files) {
    }
}
