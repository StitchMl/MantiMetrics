package com.mantimetrics.git;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class CommitMapper {
    private static final Logger  LOG      = LoggerFactory.getLogger(CommitMapper.class);
    private static final Pattern JIRA     =
            Pattern.compile("\\b(?>[A-Z][A-Z0-9]++-\\d++)\\b");
    private static final String  API      = "https://api.github.com";
    private static final String  REPOS    = "/repos/";
    private static final String  COMMIT    = "commit";
    private final GitApiClient   client;
    private static final int     MAX_COMMITS = 5_000;
    private final Map<String,Map<String,List<String>>>  projCache = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Instant>> tagDatesCache = new ConcurrentHashMap<>();

    CommitMapper(GitApiClient client) {
        this.client = client;
    }

    /** Builds a file → JIRA-keys map using the commit history of the given branch. */
    Map<String, List<String>> getFileToIssueKeysMap(String owner, String repo, String branch)
            throws FileKeyMappingException {
        String cacheKey = owner + '/' + repo;
        try {
            return projCache.computeIfAbsent(cacheKey, k -> {
                try {
                    Map<String,List<String>> m = buildFileMapViaCommits(owner, repo, branch);
                    LOG.info("Built file→JIRA map for {}: {} java files", cacheKey, m.size());
                    return Collections.unmodifiableMap(m);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new UncheckedIOException(
                            new IOException("Interrupted while building JIRA map", ie));

                } catch (IOException ioe) {
                    throw new UncheckedIOException(ioe);
                }
            });
        } catch (UncheckedIOException ex) {
            throw new FileKeyMappingException("Map build failed for "+cacheKey, ex.getCause());
        }
    }

    /**
     * Walks (up to MAX_COMMITS) commits on the given branch and builds a
     * file → JIRA-keys map using only the REST API (zero git-clone).
     */
    private Map<String, List<String>> buildFileMapViaCommits(
            String owner, String repo, String branch)
            throws IOException, InterruptedException {
        Map<String, List<String>> map = new HashMap<>();
        int fetched = 0;

        for (int page = 1; fetched < MAX_COMMITS; page++) {

            JsonNode pageCommits = client.getApi(API + REPOS + owner + '/' + repo +
                    "/commits?sha=" + URLEncoder.encode(branch, StandardCharsets.UTF_8) +
                    "&per_page=100&page=" + page);

            if (!pageCommits.isArray() || pageCommits.isEmpty()) break;

            for (JsonNode commit : pageCommits) {
                fetched++;
                processCommit(commit, map);
                if (fetched >= MAX_COMMITS) break;
            }
        }
        return map;
    }

    /** Extracts JIRA keys from the commit message and, if present, records
     *  them against every *.java* file changed in that commit. */
    private void processCommit(JsonNode commit, Map<String, List<String>> map)
            throws IOException, InterruptedException {

        List<String> keys = extractKeys(commit.path(COMMIT).path("message").asText());
        if (keys.isEmpty()) return;

        JsonNode files = client.getApi(commit.path("url").asText()).path("files");
        addKeysForFiles(files, keys, map);
    }

    /** Extracts JIRA keys from the commit message. */
    private static List<String> extractKeys(String msg) {
        List<String> keys = new ArrayList<>();
        Matcher m = JIRA.matcher(msg);
        while (m.find()) keys.add(m.group());
        return keys;
    }

    private static void addKeysForFiles(JsonNode files, List<String> keys, Map<String,List<String>> map) {
        for (JsonNode f : files) {
            String name = f.path("filename").asText("");
            if (name.endsWith(".java")) {
                // Remove any duplicates but keep the order
                List<String> unique = keys.stream()
                        .distinct()
                        .toList();
                map.put(name, unique);
            }
        }
    }

    /**
     * Returns the date of the tag, preloading if necessary.
     */
    public Instant getTagDate(String owner, String repo, String tag)
            throws IOException {
        String key = owner + "/" + repo;
        Map<String, Instant> map = tagDatesCache.computeIfAbsent(key, k -> {
            try {
                // Makes the map immutable after loading
                return Collections.unmodifiableMap(loadAllTagDates(owner, repo));
            } catch (IOException  e) {
                throw new UncheckedIOException(
                        new IOException("Unable to load date tags for " + key, e));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new UncheckedIOException(
                        new IOException("Interrupted while loading date tags for " + key, e));
            }
        });

        Instant result = map.get(tag);
        if (result == null) {
            throw new IOException("Tag not found: " + tag);
        }
        return result;
    }

    /**
     * It retrieves and maps all tags → dates in the repository in one block.
     */
    private Map<String, Instant> loadAllTagDates(String owner, String repo)
            throws IOException, InterruptedException {
        Map<String, Instant> dates = new HashMap<>();
        for (int page = 1; ; page++) {
            JsonNode tags = client.getApi(
                    API + REPOS + owner + "/" + repo +
                            "/tags?per_page=100&page=" + page);
            if (!tags.isArray() || tags.isEmpty()) break;

            for (JsonNode t : tags) {
                String name = t.path("name").asText();
                String sha  = t.path(COMMIT).path("sha").asText();

                // We get the date of the corresponding commit
                JsonNode commit = client.getApi(
                        API + REPOS + owner + "/" + repo +
                                "/commits/" + sha);
                String dateStr = commit
                        .path(COMMIT)
                        .path("committer")
                        .path("date")
                        .asText();

                Instant ts = Instant.parse(dateStr);
                dates.put(name, ts);
            }
        }
        return dates;
    }
}