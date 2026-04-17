package com.mantimetrics.git;

import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Resolves the commit SHAs belonging to a release range.
 */
final class GitHubCommitRangeClient {
    private static final String API = "https://api.github.com/repos/";

    private final GitApiClient apiClient;

    /**
     * Creates a range client backed by the shared GitHub API client.
     *
     * @param apiClient low-level GitHub API client
     */
    GitHubCommitRangeClient(GitApiClient apiClient) {
        this.apiClient = apiClient;
    }

    /**
     * Lists the commit SHAs between two release tags, or up to the first tag when no base tag exists.
     *
     * @param owner repository owner
     * @param repo repository name
     * @param baseTag previous tag, or blank for the first release
     * @param headTag current tag
     * @return ordered commit SHAs belonging to the release range
     * @throws IOException when GitHub data cannot be fetched
     * @throws InterruptedException when the thread is interrupted while waiting for the API
     */
    List<String> listCommitShas(String owner, String repo, String baseTag, String headTag)
            throws IOException, InterruptedException {
        return isBlank(baseTag)
                ? listCommitsUntilTag(owner, repo, headTag)
                : compareCommits(owner, repo, baseTag, headTag);
    }

    /**
     * Lists the commits reachable from the first release tag and reverses them into chronological order.
     *
     * @param owner repository owner
     * @param repo repository name
     * @param headTag current tag
     * @return chronological commit SHAs up to the first release tag
     * @throws IOException when GitHub data cannot be fetched
     * @throws InterruptedException when the thread is interrupted while waiting for the API
     */
    private List<String> listCommitsUntilTag(String owner, String repo, String headTag)
            throws IOException, InterruptedException {
        String encodedHead = URLEncoder.encode(headTag, StandardCharsets.UTF_8);
        String template = API + owner + "/" + repo + "/commits?sha=" + encodedHead + "&per_page=100&page=%d";
        List<String> shas = new ArrayList<>();
        for (int page = 1; ; page++) {
            JsonNode response = apiClient.getApi(String.format(template, page));
            if (!response.isArray() || response.isEmpty()) {
                break;
            }
            response.forEach(commit -> addSha(commit, shas));
        }
        Collections.reverse(shas);
        return List.copyOf(shas);
    }

    /**
     * Lists the commits contained in the GitHub compare range between two tags.
     *
     * @param owner repository owner
     * @param repo repository name
     * @param baseTag previous tag
     * @param headTag current tag
     * @return ordered commit SHAs in the compare range
     * @throws IOException when GitHub data cannot be fetched
     * @throws InterruptedException when the thread is interrupted while waiting for the API
     */
    private List<String> compareCommits(String owner, String repo, String baseTag, String headTag)
            throws IOException, InterruptedException {
        String encodedBaseHead = URLEncoder.encode(baseTag + "..." + headTag, StandardCharsets.UTF_8);
        String template = API + owner + "/" + repo + "/compare/" + encodedBaseHead + "?per_page=100&page=%d";
        List<String> shas = new ArrayList<>();
        for (int page = 1; ; page++) {
            JsonNode response = apiClient.getApi(String.format(template, page));
            JsonNode commits = response.path("commits");
            if (!commits.isArray() || commits.isEmpty()) {
                break;
            }
            commits.forEach(commit -> addSha(commit, shas));
        }
        return List.copyOf(shas);
    }

    /**
     * Extracts a commit SHA from the GitHub response.
     *
     * @param commit JSON node describing one commit
     * @param shas output list receiving the SHA
     */
    private static void addSha(JsonNode commit, List<String> shas) {
        String sha = commit.path("sha").asText(null);
        if (sha == null || sha.isBlank()) {
            throw new UncheckedIOException(new IOException("Missing commit sha in GitHub response"));
        }
        shas.add(sha);
    }

    /**
     * Checks whether a tag value is null, empty or blank.
     *
     * @param value value to inspect
     * @return {@code true} when the value is blank
     */
    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
