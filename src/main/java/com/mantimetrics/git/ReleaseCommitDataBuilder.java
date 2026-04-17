package com.mantimetrics.git;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Aggregates commit-level snapshots into file-level release metadata.
 */
final class ReleaseCommitDataBuilder {

    /**
     * Builds release-level file aggregates from the commit snapshots belonging to a release range.
     *
     * @param commits commit snapshots collected for the release range
     * @return immutable release commit data grouped by relative path
     */
    static ReleaseCommitData aggregate(List<ReleaseCommitSnapshot> commits) {
        Map<String, List<String>> touchMap = new HashMap<>();
        Map<String, List<String>> issueTouchMap = new HashMap<>();
        Map<String, List<String>> fileToIssueKeys = new HashMap<>();
        Map<String, List<String>> authorMap = new HashMap<>();
        Map<String, Integer> additionsMap = new HashMap<>();
        Map<String, Integer> deletionsMap = new HashMap<>();

        for (ReleaseCommitSnapshot commit : commits) {
            Set<ReleaseCommitFile> javaFiles = commit.files().stream()
                    .filter(file -> file.path().endsWith(".java"))
                    .collect(Collectors.toCollection(LinkedHashSet::new));

            if (javaFiles.isEmpty()) {
                continue;
            }

            Set<String> javaPaths = javaFiles.stream()
                    .map(ReleaseCommitFile::path)
                    .collect(Collectors.toCollection(LinkedHashSet::new));

            for (ReleaseCommitFile file : javaFiles) {
                touchMap.computeIfAbsent(file.path(), ignored -> new ArrayList<>()).add(commit.sha());
                if (!commit.author().isBlank()) {
                    authorMap.computeIfAbsent(file.path(), ignored -> new ArrayList<>()).add(commit.author());
                }
                additionsMap.merge(file.path(), file.additions(), Integer::sum);
                deletionsMap.merge(file.path(), file.deletions(), Integer::sum);
            }

            List<String> issueKeys = IssueKeySupport.extractKeys(commit.message());
            if (!issueKeys.isEmpty()) {
                for (String file : javaPaths) {
                    issueTouchMap.computeIfAbsent(file, ignored -> new ArrayList<>()).add(commit.sha());
                }
                IssueKeySupport.addKeysForFiles(javaPaths, issueKeys, fileToIssueKeys);
            }
        }

        return new ReleaseCommitData(
                immutableCopy(touchMap),
                immutableCopy(issueTouchMap),
                immutableCopy(fileToIssueKeys),
                immutableCopy(authorMap),
                Map.copyOf(additionsMap),
                Map.copyOf(deletionsMap)
        );
    }

    /**
     * Copies a map of mutable lists into an immutable equivalent.
     *
     * @param source source map containing mutable list values
     * @return immutable map with immutable list values
     */
    private static Map<String, List<String>> immutableCopy(Map<String, List<String>> source) {
        Map<String, List<String>> copy = new HashMap<>();
        source.forEach((key, value) -> copy.put(key, List.copyOf(value)));
        return Collections.unmodifiableMap(copy);
    }

    /**
     * Immutable snapshot of one commit enriched with author, message and touched files.
     *
     * @param sha commit SHA
     * @param message commit message
     * @param author commit author name
     * @param files changed files touched by the commit
     */
    record ReleaseCommitSnapshot(String sha, String message, String author, Set<ReleaseCommitFile> files) {
    }

    /**
     * Immutable snapshot of one file changed by a commit.
     *
     * @param path relative file path
     * @param additions added lines reported by GitHub
     * @param deletions deleted lines reported by GitHub
     */
    record ReleaseCommitFile(String path, int additions, int deletions) {
    }
}
