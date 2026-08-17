package com.mantimetrics.gitissue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Fetches closed, bug-labeled GitHub Issues for a repository, used as a complementary
 * defect source to Jira. Pull requests are skipped (the /issues endpoint returns both).
 */
public final class GitIssueClient {
    private static final Logger LOG = LoggerFactory.getLogger(GitIssueClient.class);
    private static final int PAGE_SIZE = 100;

    private final OkHttpClient http;
    private final ObjectMapper json = new ObjectMapper();
    private final String token;

    /**
     * Creates a client configured with a GitHub personal access token.
     *
     * @param token GitHub personal access token
     */
    public GitIssueClient(String token) {
        this.token = token;
        this.http = new OkHttpClient.Builder()
                .connectTimeout(Duration.ofSeconds(30))
                .readTimeout(Duration.ofSeconds(60))
                .callTimeout(Duration.ofSeconds(90))
                .retryOnConnectionFailure(true)
                .build();
    }

    /**
     * Fetches all closed issues carrying the bug label for a repository, following pagination.
     *
     * @param owner repository owner
     * @param repo repository name
     * @return list of raw GitHub bug issues (number + creation timestamp)
     * @throws IOException when a request fails permanently
     */
    public List<RawIssue> fetchClosedBugIssues(String owner, String repo) throws IOException {
        List<RawIssue> issues = new ArrayList<>();
        int page = 1;
        boolean done = false;

        while (!done) {
            String url = String.format(
                    "%s/repos/%s/%s/issues?state=%s&labels=%s&per_page=%d&page=%d",
                    GitIssueConfig.API_BASE, owner, repo,
                    GitIssueConfig.STATE, GitIssueConfig.BUG_LABEL, PAGE_SIZE, page);

            JsonNode array = get(url);

            if (!array.isArray() || array.isEmpty()) {
                done = true;
            } else {
                for (JsonNode node : array) {
                    // /issues also returns PRs; skip them
                    if (!node.has("pull_request")) {
                        issues.add(new RawIssue(
                                node.path("number").asInt(),
                                Instant.parse(node.path("created_at").asText())));
                    }
                }

                done = array.size() < PAGE_SIZE;

                if (!done) {
                    page++;
                }
            }
        }
        LOG.info("GitHub Issues: fetched {} closed bug issues for {}/{}", issues.size(), owner, repo);
        return issues;
    }

    /**
     * Performs a GitHub API GET request and parses the JSON response.
     *
     * @param url fully qualified GitHub API URL
     * @return parsed JSON response
     * @throws IOException when the request fails or returns a non-success status
     */
    private JsonNode get(String url) throws IOException {
        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", "token " + token)
                .header("Accept", "application/vnd.github.v3+json")
                .build();
        try (Response response = http.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new IOException("HTTP " + response.code() + " for " + url);
            }
            return json.readTree(response.body().string());
        }
    }

    /**
     * Minimal raw GitHub issue payload needed by the labeling flow.
     *
     * @param number GitHub issue number
     * @param createdAt issue creation timestamp
     */
    public record RawIssue(int number, Instant createdAt) {
    }
}
