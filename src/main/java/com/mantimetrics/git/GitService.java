package com.mantimetrics.git;

import com.fasterxml.jackson.databind.JsonNode;
import org.eclipse.jgit.revwalk.RevCommit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

/** Git + GitHub REST utility – *commit-history* implementation */
public final class GitService {
    private static final Logger      LOG       = LoggerFactory.getLogger(GitService.class);
    private static final String      API       = "https://api.github.com";
    private static final String      REPOS     = "/repos/";

    private final GitApiClient          apiClient;
    private final CommitMapper          commitMapper;
    private final ZipDownloader         zipDownloader;
    private final Map<String,String>    defaultBranchCache = new ConcurrentHashMap<>();

    public GitService(String token) {
        this.apiClient     = new GitApiClient(token);
        this.commitMapper  = new CommitMapper(apiClient);
        this.zipDownloader = new ZipDownloader(apiClient);
    }

    /**
     * Calls the GitHub API and returns the JSON node, handling
     * IOException and InterruptedException.
     */
    private JsonNode callApi(String url) {
        try {
            return apiClient.getApi(url);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new UncheckedIOException(new IOException("Interrupted during API call to " + url, ie));
        } catch (IOException ioe) {
            throw new UncheckedIOException("I/O error during API call to " + url, ioe);
        }
    }

    /**
     * Iterated paging on the results (when the endpoint responds
     * with JSON arrays).
     */
    private List<JsonNode> fetchPaged(String urlTemplate) {
        List<JsonNode> result = new ArrayList<>();
        for (int page = 1; ; page++) {
            String url = String.format(urlTemplate, page);
            JsonNode array = callApi(url);
            if (!array.isArray() || array.isEmpty()) break;
            array.forEach(result::add);
        }
        return result;
    }

    /** Returns the default branch of the given repository. */
    public String getDefaultBranch(String owner, String repo) {
        String key = owner + '/' + repo;
        return defaultBranchCache.computeIfAbsent(key, k -> {
            String url = API + REPOS + owner + "/" + repo;
            JsonNode node = callApi(url);
            String branch = node.path("default_branch").asText(null);
            LOG.info("Default branch for {}/{} → {}", owner, repo, branch);
            return branch != null ? branch : "master";
        });
    }

    /** Lists all tags of the given repository. */
    public List<String> listTags(String owner, String repo) {
        String template = API + REPOS + owner + "/" + repo +
                "/tags?per_page=100&page=%d";
        List<JsonNode> nodes = fetchPaged(template);
        List<String> tags = new ArrayList<>(nodes.size());
        for (JsonNode n : nodes) {
            Optional.ofNullable(n.path("name").asText(null))
                    .ifPresent(tags::add);
        }
        LOG.info("Found {} tags for {}/{}", tags.size(), owner, repo);
        return tags;
    }

    /**
     * Builds a file → JIRA-keys map using the commit history
     * of the given branch.
     */
    public Map<String,List<String>> getFileToIssueKeysMap(
            String owner, String repo, String branch) throws FileKeyMappingException {
        return commitMapper.getFileToIssueKeysMap(owner, repo, branch);
    }

    /** download + unzip con timeout esteso e retry su SocketTimeout. */
    public Path downloadAndUnzipRepo(
            String owner, String repo,
            String ref, String subDir)
            throws IOException, InterruptedException {
        return zipDownloader.downloadAndUnzip(owner, repo, ref, subDir);
    }

    /** Deletes the temporary directory and all its contents. */
    public List<Path> getTmp() {
        return zipDownloader.getTmpDirs();
    }

    /**
     * Chronological comparison of two tags in the same repo.
     */
    public int compareTagDates(
            String owner, String repo,
            String tagA, String tagB) {
        try {
            Instant a = commitMapper.getTagDate(owner, repo, tagA);
            Instant b = commitMapper.getTagDate(owner, repo, tagB);
            return a.compareTo(b);
        } catch (IOException e) {
            throw new UncheckedIOException(
                    new IOException("Cannot retrieve tag dates for " + owner + "/" + repo, e));
        }
    }

    /**
     * Builds a map of files to commit SHAs that modified them
     * between the two given tags (exclusive prevTag, inclusive tag).
     * The returned map is file → list of commit SHAs.
     */
    public Map<String, List<String>> buildTouchMap(
            String owner, String repo, String prevTag, String tag) throws IOException, InterruptedException {
        List<String> shaCode = apiClient.listCommitsBetween(owner, repo, prevTag, tag);

        LocalRepoCache cache = LocalRepoCacheManager.obtain(owner, repo);

        Map<String,List<String>> map = new HashMap<>();
        for (String sha : shaCode) {
            RevCommit commit = cache.lookup(sha);
            for (String file : cache.filesOf(commit)) {
                map.computeIfAbsent(file, k -> new ArrayList<>()).add(sha);
            }
        }
        return map;
    }
}