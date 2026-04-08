package com.mantimetrics.git;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class GitHubReleaseCommitDataClientTest {

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
                  "commit":{"message":"PROJ-1 Fix parser regression"},
                  "files":[
                    {"filename":"src/main/java/com/acme/Sample.java"},
                    {"filename":"README.md"}
                  ]
                }
                """);
        apiClient.when(
                "https://api.github.com/repos/apache/demo/commits/sha-2?per_page=100&page=1",
                """
                {
                  "commit":{"message":"Refactor parser internals"},
                  "files":[
                    {"filename":"src/main/java/com/acme/Sample.java"},
                    {"filename":"src/main/java/com/acme/Extra.java"}
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
    }

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
                  "commit":{"message":"PROJ-7 Initial parser fix"},
                  "files":[{"filename":"src/main/java/com/acme/Sample.java"}]
                }
                """);
        apiClient.when(
                "https://api.github.com/repos/apache/demo/commits/sha-2?per_page=100&page=1",
                """
                {
                  "commit":{"message":"Second change without bug key"},
                  "files":[{"filename":"src/main/java/com/acme/Sample.java"}]
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
    }
}
