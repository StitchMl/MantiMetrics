package com.mantimetrics.git;

import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

final class GitHubCommitDetailsClient {
    private static final String API = "https://api.github.com/repos/";

    private final GitApiClient apiClient;
    private final ConcurrentMap<String, ReleaseCommitDataBuilder.ReleaseCommitSnapshot> snapshotCache =
            new ConcurrentHashMap<>();

    GitHubCommitDetailsClient(GitApiClient apiClient) {
        this.apiClient = apiClient;
    }

    ReleaseCommitDataBuilder.ReleaseCommitSnapshot fetch(String owner, String repo, String sha)
            throws IOException, InterruptedException {
        String key = owner + '/' + repo + '@' + sha;
        ReleaseCommitDataBuilder.ReleaseCommitSnapshot cached = snapshotCache.get(key);
        if (cached != null) {
            return cached;
        }

        ReleaseCommitDataBuilder.ReleaseCommitSnapshot snapshot = fetchUncached(owner, repo, sha);
        ReleaseCommitDataBuilder.ReleaseCommitSnapshot previous = snapshotCache.putIfAbsent(key, snapshot);
        return previous != null ? previous : snapshot;
    }

    private ReleaseCommitDataBuilder.ReleaseCommitSnapshot fetchUncached(String owner, String repo, String sha)
            throws IOException, InterruptedException {
        String encodedSha = URLEncoder.encode(sha, StandardCharsets.UTF_8);
        String template = API + owner + "/" + repo + "/commits/" + encodedSha + "?per_page=100&page=%d";

        String message = null;
        String author = "";
        Set<ReleaseCommitDataBuilder.ReleaseCommitFile> files = new LinkedHashSet<>();
        for (int page = 1; ; page++) {
            JsonNode response = apiClient.getApi(String.format(template, page));
            if (message == null) {
                message = response.path("commit").path("message").asText("");
                author = response.path("commit").path("author").path("name").asText("");
            }

            JsonNode fileNodes = response.path("files");
            if (!fileNodes.isArray() || fileNodes.isEmpty()) {
                break;
            }
            fileNodes.forEach(file -> addFilename(file, files));
            if (fileNodes.size() < 100) {
                break;
            }
        }

        return new ReleaseCommitDataBuilder.ReleaseCommitSnapshot(
                sha,
                message == null ? "" : message,
                author,
                files
        );
    }

    private static void addFilename(JsonNode fileNode, Set<ReleaseCommitDataBuilder.ReleaseCommitFile> files) {
        String filename = fileNode.path("filename").asText(null);
        if (filename == null || filename.isBlank()) {
            throw new UncheckedIOException(new IOException("Missing filename in GitHub commit response"));
        }
        files.add(new ReleaseCommitDataBuilder.ReleaseCommitFile(
                filename,
                fileNode.path("additions").asInt(0),
                fileNode.path("deletions").asInt(0)
        ));
    }
}
