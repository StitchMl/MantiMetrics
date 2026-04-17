package com.mantimetrics.git;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Tests for {@link GitHubReleaseCommitDataClient}.
 */
class GitHubReleaseCommitDataClientTest {

    /**
     * Verifies that paginated compare responses and commit details are merged into one release aggregate.
     */
    @Test
    void buildsReleaseCommitDataFromPaginatedCompareAndCommitDetails() throws Exception {
        TestGitApiClient apiClient = new TestGitApiClient();
        apiClient.when(
                "https://api.github.com/repos/apache/demo/compare/v1.0...v1.1?per_page=100&page=1",
                """
                {"commits":[{"sha":"sha-1"},{"sha":"sha-2"}]}
                """);
        apiClient.when(
                "https://api.github.com/repos/apache/demo/compare/v1.0...v1.1?per_page=100&page=2",
                """
                {"commits":[]}
                """);
        apiClient.when(
                "https://api.github.com/repos/apache/demo/commits/sha-1?per_page=100&page=1",
                """
                {
                  "commit":{"message":"PROJ-1 Fix parser regression","author":{"name":"Alice"}},
                  "files":[
                    {"filename":"src/main/java/com/acme/Sample.java","additions":10,"deletions":2},
                    {"filename":"README.md","additions":1,"deletions":0}
                  ]
                }
                """);
        apiClient.when(
                "https://api.github.com/repos/apache/demo/commits/sha-2?per_page=100&page=1",
                """
                {
                  "commit":{"message":"Refactor parser internals","author":{"name":"Bob"}},
                  "files":[
                    {"filename":"src/main/java/com/acme/Sample.java","additions":4,"deletions":1},
                    {"filename":"src/main/java/com/acme/Extra.java","additions":2,"deletions":2}
                  ]
                }
                """);

        ReleaseCommitData data = new GitHubReleaseCommitDataClient(apiClient)
                .build("apache", "demo", "v1.0", "v1.1");

        assertEquals(
                List.of("sha-1", "sha-2"),
                data.touchMap().get("src/main/java/com/acme/Sample.java"));
        assertEquals(
                List.of("sha-2"),
                data.touchMap().get("src/main/java/com/acme/Extra.java"));
        assertEquals(
                List.of("PROJ-1"),
                data.fileToIssueKeys().get("src/main/java/com/acme/Sample.java"));
        assertFalse(data.fileToIssueKeys().containsKey("src/main/java/com/acme/Extra.java"));
        assertEquals(List.of("Alice", "Bob"), data.authorMap().get("src/main/java/com/acme/Sample.java"));
        assertEquals(14, data.additionsFor("src/main/java/com/acme/Sample.java"));
        assertEquals(3, data.deletionsFor("src/main/java/com/acme/Sample.java"));
    }

    /**
     * Verifies that the first release is built from commit history when no previous tag exists.
     */
    @Test
    void buildsFirstReleaseDataFromCommitHistoryWithoutPreviousTag() throws Exception {
        TestGitApiClient apiClient = new TestGitApiClient();
        apiClient.when(
                "https://api.github.com/repos/apache/demo/commits?sha=v1.0&per_page=100&page=1",
                """
                [{"sha":"sha-2"},{"sha":"sha-1"}]
                """);
        apiClient.when(
                "https://api.github.com/repos/apache/demo/commits?sha=v1.0&per_page=100&page=2",
                """
                []
                """);
        apiClient.when(
                "https://api.github.com/repos/apache/demo/commits/sha-1?per_page=100&page=1",
                """
                {
                  "commit":{"message":"PROJ-7 Initial parser fix","author":{"name":"Alice"}},
                  "files":[{"filename":"src/main/java/com/acme/Sample.java","additions":7,"deletions":1}]
                }
                """);
        apiClient.when(
                "https://api.github.com/repos/apache/demo/commits/sha-2?per_page=100&page=1",
                """
                {
                  "commit":{"message":"Second change without bug key","author":{"name":"Bob"}},
                  "files":[{"filename":"src/main/java/com/acme/Sample.java","additions":5,"deletions":0}]
                }
                """);

        ReleaseCommitData data = new GitHubReleaseCommitDataClient(apiClient)
                .build("apache", "demo", null, "v1.0");

        assertEquals(
                List.of("sha-1", "sha-2"),
                data.touchMap().get("src/main/java/com/acme/Sample.java"));
        assertEquals(
                List.of("PROJ-7"),
                data.fileToIssueKeys().get("src/main/java/com/acme/Sample.java"));
        assertEquals(List.of("Alice", "Bob"), data.authorMap().get("src/main/java/com/acme/Sample.java"));
        assertEquals(12, data.additionsFor("src/main/java/com/acme/Sample.java"));
    }
}
