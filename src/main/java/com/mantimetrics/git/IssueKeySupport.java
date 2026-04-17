package com.mantimetrics.git;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Helper methods for extracting Jira issue keys from commits and associating them with Java files.
 */
final class IssueKeySupport {
    private static final Pattern JIRA_KEY_PATTERN =
            Pattern.compile("\\b(?>[A-Z][A-Z0-9]++-\\d++)\\b");

    /**
     * Prevents instantiation of the static utility class.
     */
    private IssueKeySupport() {
        throw new AssertionError("Do not instantiate IssueKeySupport");
    }

    /**
     * Extracts Jira issue keys from a commit message.
     *
     * @param message commit message to inspect
     * @return issue keys in encounter order
     */
    static List<String> extractKeys(String message) {
        List<String> keys = new ArrayList<>();
        Matcher matcher = JIRA_KEY_PATTERN.matcher(message);
        while (matcher.find()) {
            keys.add(matcher.group());
        }
        return keys;
    }

    /**
     * Associates the provided issue keys with the Java files described by a GitHub API payload.
     *
     * @param files GitHub JSON array describing changed files
     * @param keys issue keys found in the commit message
     * @param map target map keyed by relative Java file path
     */
    static void addKeysForFiles(JsonNode files, List<String> keys, Map<String, List<String>> map) {
        List<String> javaFiles = new ArrayList<>();
        for (JsonNode file : files) {
            String name = file.path("filename").asText("");
            if (name.endsWith(".java")) {
                javaFiles.add(name);
            }
        }
        addKeysForFiles(javaFiles, keys, map);
    }

    /**
     * Associates the provided issue keys with the supplied Java file paths.
     *
     * @param files changed file paths
     * @param keys issue keys found in the commit message
     * @param map target map keyed by relative Java file path
     */
    static void addKeysForFiles(Collection<String> files, List<String> keys, Map<String, List<String>> map) {
        List<String> unique = keys.stream().distinct().toList();
        if (unique.isEmpty()) {
            return;
        }

        for (String file : files) {
            if (!file.endsWith(".java")) {
                continue;
            }
            LinkedHashSet<String> merged = new LinkedHashSet<>(map.getOrDefault(file, List.of()));
            merged.addAll(unique);
            map.put(file, List.copyOf(merged));
        }
    }
}
