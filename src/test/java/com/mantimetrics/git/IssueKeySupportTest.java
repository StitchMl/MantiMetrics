package com.mantimetrics.git;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Tests for {@link IssueKeySupport}.
 */
class IssueKeySupportTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    /**
     * Verifies that distinct issue keys accumulate across multiple commits touching the same Java file.
     */
    @Test
    void addKeysForFilesAccumulatesDistinctKeysAcrossCommits() {
        ArrayNode firstCommitFiles = JSON.createArrayNode();
        firstCommitFiles.addObject().put("filename", "src/main/java/com/acme/Sample.java");

        ArrayNode secondCommitFiles = JSON.createArrayNode();
        secondCommitFiles.addObject().put("filename", "src/main/java/com/acme/Sample.java");

        Map<String, List<String>> map = new HashMap<>();

        IssueKeySupport.addKeysForFiles(firstCommitFiles, List.of("PROJ-1", "PROJ-1"), map);
        IssueKeySupport.addKeysForFiles(secondCommitFiles, List.of("PROJ-2", "PROJ-1"), map);

        assertEquals(
                List.of("PROJ-1", "PROJ-2"),
                map.get("src/main/java/com/acme/Sample.java"));
    }

    /**
     * Verifies that non-Java files are ignored when mapping issue keys to files.
     */
    @Test
    void addKeysForFilesSkipsNonJavaFiles() {
        ArrayNode files = JSON.createArrayNode();
        files.addObject().put("filename", "README.md");

        Map<String, List<String>> map = new HashMap<>();
        IssueKeySupport.addKeysForFiles(files, List.of("PROJ-1"), map);

        assertFalse(map.containsKey("README.md"));
    }
}
