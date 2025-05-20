package com.mantimetrics.git;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
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
    private final GitApiClient   client;
    private static final int     MAX_COMMITS = 5_000;
    private final Map<String,Map<String,List<String>>>  projCache = new ConcurrentHashMap<>();

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

        List<String> keys = extractKeys(commit.path("commit").path("message").asText());
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
                map.computeIfAbsent(name, k -> new ArrayList<>()).addAll(keys);
            }
        }
    }
}