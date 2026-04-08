package com.mantimetrics.git;

import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class GitHubCommitRangeClient {
    private static final String API = "https://api.github.com/repos/";

    private final GitApiClient apiClient;

    GitHubCommitRangeClient(GitApiClient apiClient) {
        this.apiClient = apiClient;
    }

    List<String> listCommitShas(String owner, String repo, String baseTag, String headTag)
            throws IOException, InterruptedException {
        return isBlank(baseTag)
                ? listCommitsUntilTag(owner, repo, headTag)
                : compareCommits(owner, repo, baseTag, headTag);
    }

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

    private static void addSha(JsonNode commit, List<String> shas) {
        String sha = commit.path("sha").asText(null);
        if (sha == null || sha.isBlank()) {
            throw new UncheckedIOException(new IOException("Missing commit sha in GitHub response"));
        }
        shas.add(sha);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
