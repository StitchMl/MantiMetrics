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

/**
 * Fetches and caches per-commit metadata needed to build release commit aggregates.
 */
final class GitCommitDetails {
    private static final String API = "https://api.github.com/repos/";

    private final GitClient apiClient;
    private final ConcurrentMap<String, GitPrevReleaseBuilder.ReleaseCommitSnapshot> snapshotCache =
            new ConcurrentHashMap<>();

    /**
     * Creates a commit-details client backed by the shared GitHub API client.
     *
     * @param apiClient low-level GitHub API client
     */
    GitCommitDetails(GitClient apiClient) {
        this.apiClient = apiClient;
    }

    /**
     * Returns the cached or freshly fetched snapshot for a commit SHA.
     *
     * @param owner repository owner
     * @param repo repository name
     * @param sha commit SHA to inspect
     * @return immutable snapshot of the commit details
     * @throws IOException when GitHub data cannot be fetched
     * @throws InterruptedException when the thread is interrupted while waiting for the API
     */
    GitPrevReleaseBuilder.ReleaseCommitSnapshot fetch(String owner, String repo, String sha)
            throws IOException, InterruptedException {
        String key = owner + '/' + repo + '@' + sha;
        GitPrevReleaseBuilder.ReleaseCommitSnapshot cached = snapshotCache.get(key);
        if (cached != null) {
            return cached;
        }

        GitPrevReleaseBuilder.ReleaseCommitSnapshot snapshot = fetchUncached(owner, repo, sha);
        GitPrevReleaseBuilder.ReleaseCommitSnapshot previous = snapshotCache.putIfAbsent(key, snapshot);
        return previous != null ? previous : snapshot;
    }

    /**
     * Fetches the commit details from GitHub without consulting the local cache.
     *
     * @param owner repository owner
     * @param repo repository name
     * @param sha commit SHA to inspect
     * @return immutable snapshot of the commit details
     * @throws IOException when GitHub data cannot be fetched
     * @throws InterruptedException when the thread is interrupted while waiting for the API
     */
    private GitPrevReleaseBuilder.ReleaseCommitSnapshot fetchUncached(String owner, String repo, String sha)
            throws IOException, InterruptedException {
        String encodedSha = URLEncoder.encode(sha, StandardCharsets.UTF_8);
        String template = API + owner + "/" + repo + "/commits/" + encodedSha + "?per_page=100&page=%d";

        String message = null;
        String author = "";
        Set<GitPrevReleaseBuilder.ReleaseCommitFile> files = new LinkedHashSet<>();
        int page = 1;
        boolean hasMore = true;
        while (hasMore) {
            JsonNode response = apiClient.getApi(String.format(template, page));
            if (message == null) {
                message = response.path("commit").path("message").asText("");
                author = response.path("commit").path("author").path("name").asText("");
            }

            JsonNode fileNodes = response.path("files");
            if (!fileNodes.isArray() || fileNodes.isEmpty() || fileNodes.size() < 100) {
                hasMore = false;
            } else {
                page++;
            }
            if (fileNodes.isArray()) {
                fileNodes.forEach(file -> addFilename(file, files));
            }
        }

        return new GitPrevReleaseBuilder.ReleaseCommitSnapshot(
                sha,
                message == null ? "" : message,
                author,
                files
        );
    }

    /**
     * Extracts one changed-file entry from the GitHub response.
     *
     * @param fileNode JSON node describing a changed file
     * @param files output set receiving the normalized file snapshot
     */
    private static void addFilename(JsonNode fileNode, Set<GitPrevReleaseBuilder.ReleaseCommitFile> files) {
        String filename = fileNode.path("filename").asText(null);
        if (filename == null || filename.isBlank()) {
            throw new UncheckedIOException(new IOException("Missing filename in GitHub commit response"));
        }
        files.add(new GitPrevReleaseBuilder.ReleaseCommitFile(
                filename,
                fileNode.path("additions").asInt(0),
                fileNode.path("deletions").asInt(0)
        ));
    }
}
