package com.mantimetrics.git;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link GitHubRepositoryClient}.
 */
class GitHubRepositoryClientTest {

    /**
     * Verifies that tag dates are cached across repeated comparisons.
     */
    @Test
    void cachesTagDatesAcrossComparisons() throws Exception {
        TestGitApiClient apiClient = new TestGitApiClient();
        String leftUrl = "https://api.github.com/repos/apache/demo/commits/v1.0";
        String rightUrl = "https://api.github.com/repos/apache/demo/commits/v2.0";
        apiClient.when(leftUrl, """
                {"commit":{"committer":{"date":"2024-01-01T00:00:00Z"}}}
                """);
        apiClient.when(rightUrl, """
                {"commit":{"committer":{"date":"2024-02-01T00:00:00Z"}}}
                """);

        GitHubRepositoryClient client = new GitHubRepositoryClient(apiClient);
        assertTrue(client.compareTagDates("apache", "demo", "v1.0", "v2.0") < 0);
        assertTrue(client.compareTagDates("apache", "demo", "v1.0", "v2.0") < 0);

        assertEquals(1, apiClient.calls(leftUrl));
        assertEquals(1, apiClient.calls(rightUrl));
    }
}
