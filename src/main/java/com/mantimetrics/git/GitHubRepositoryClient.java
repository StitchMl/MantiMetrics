package com.mantimetrics.git;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Repository-level GitHub client for metadata such as tags, default branches and commit dates.
 */
final class GitHubRepositoryClient {
    private static final Logger LOG = LoggerFactory.getLogger(GitHubRepositoryClient.class);
    private static final String API = "https://api.github.com";
    private static final String REPOS = "/repos/";

    private final GitApiClient apiClient;
    private final ConcurrentMap<String, String> defaultBranchCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Instant> tagDateCache = new ConcurrentHashMap<>();

    /**
     * Creates a repository client backed by the shared GitHub API client.
     *
     * @param apiClient low-level GitHub API client
     */
    GitHubRepositoryClient(GitApiClient apiClient) {
        this.apiClient = apiClient;
    }

    /**
     * Returns the repository default branch, caching the result.
     *
     * @param owner repository owner
     * @param repo repository name
     * @return default branch name, falling back to {@code master} when GitHub omits it
     */
    String getDefaultBranch(String owner, String repo) {
        String key = owner + '/' + repo;
        return defaultBranchCache.computeIfAbsent(key, ignored -> {
            JsonNode node = callApi(API + REPOS + owner + "/" + repo);
            String branch = node.path("default_branch").asText(null);
            LOG.info("Default branch for {}/{} -> {}", owner, repo, branch);
            return branch != null ? branch : "master";
        });
    }

    /**
     * Lists every tag published by the repository.
     *
     * @param owner repository owner
     * @param repo repository name
     * @return tags returned by GitHub in API order
     */
    List<String> listTags(String owner, String repo) {
        String template = API + REPOS + owner + "/" + repo + "/tags?per_page=100&page=%d";
        List<JsonNode> nodes = fetchPaged(template);
        List<String> tags = new ArrayList<>(nodes.size());
        for (JsonNode node : nodes) {
            Optional.ofNullable(node.path("name").asText(null)).ifPresent(tags::add);
        }
        LOG.info("Found {} tags for {}/{}", tags.size(), owner, repo);
        return tags;
    }

    /**
     * Compares two tags by their commit date.
     *
     * @param owner repository owner
     * @param repo repository name
     * @param leftTag left tag to compare
     * @param rightTag right tag to compare
     * @return negative, zero or positive depending on the chronological order
     */
    int compareTagDates(String owner, String repo, String leftTag, String rightTag) {
        return fetchTagDate(owner, repo, leftTag).compareTo(fetchTagDate(owner, repo, rightTag));
    }

    /**
     * Resolves and caches the commit date associated with a tag.
     *
     * @param owner repository owner
     * @param repo repository name
     * @param tag tag to inspect
     * @return commit date of the tag
     */
    private Instant fetchTagDate(String owner, String repo, String tag) {
        String key = owner + '/' + repo + '@' + tag;
        return tagDateCache.computeIfAbsent(key, ignored -> {
            JsonNode node = callApi(API + REPOS + owner + "/" + repo + "/commits/" + tag);
            String date = node.path("commit").path("committer").path("date").asText(null);
            if (date == null || date.isBlank()) {
                throw new UncheckedIOException(new IOException("Missing commit date for tag " + tag));
            }
            return Instant.parse(date);
        });
    }

    /**
     * Follows paginated GitHub endpoints until an empty page is returned.
     *
     * @param urlTemplate paginated GitHub API URL template containing one integer placeholder
     * @return concatenated JSON nodes from all fetched pages
     */
    private List<JsonNode> fetchPaged(String urlTemplate) {
        List<JsonNode> result = new ArrayList<>();
        for (int page = 1; ; page++) {
            JsonNode array = callApi(String.format(urlTemplate, page));
            if (!array.isArray() || array.isEmpty()) {
                break;
            }
            array.forEach(result::add);
        }
        return result;
    }

    /**
     * Wraps a checked API call into an unchecked one for use inside cache lambdas.
     *
     * @param url fully qualified GitHub API URL
     * @return parsed JSON response
     */
    private JsonNode callApi(String url) {
        try {
            return apiClient.getApi(url);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new UncheckedIOException(new IOException("Interrupted during API call to " + url, exception));
        } catch (IOException exception) {
            throw new UncheckedIOException("I/O error during API call to " + url, exception);
        }
    }
}
