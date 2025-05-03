package com.mantimetrics.git;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GitService {
    private static final String API_URL = "https://api.github.com";
    private static final String RAW_URL = "https://raw.githubusercontent.com";
    private final OkHttpClient client = new OkHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();
    private final String authToken;
    // Cache default branches per repo to avoid repeated calls
    private final java.util.Map<String, String> branchCache = new java.util.HashMap<>();
    private static final Pattern JIRA_KEY_PATTERN = Pattern.compile("([A-Z]+-\\d+)");

    public GitService(String pat) {
        this.authToken = pat;
    }

    /**
     * Returns all JIRA issue keys found in commit messages
     * for each change to the filePath on the default branch.
     */
    public List<String> getIssueKeysForFile(String owner, String repo, String filePath) throws IOException {
        String branch = getDefaultBranch(owner, repo);
        String url = String.format("%s/repos/%s/%s/commits?sha=%s&path=%s",
                API_URL,
                owner,
                repo,
                URLEncoder.encode(branch, StandardCharsets.UTF_8),
                URLEncoder.encode(filePath, StandardCharsets.UTF_8));

        Request req = new Request.Builder()
                .url(url)
                .header("Authorization", "token " + authToken)
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "MantiMetricsApp")    // ← obbligatorio
                .build();

        try (Response resp = client.newCall(req).execute()) {
            if (!resp.isSuccessful()) {
                throw new IOException("GitHub Commits API error: HTTP " + resp.code());
            }
            assert resp.body() != null;
            JsonNode commits = mapper.readTree(resp.body().string());
            Set<String> keys = new HashSet<>();
            for (JsonNode c : commits) {
                String msg = c.path("commit").path("message").asText("");
                Matcher m = JIRA_KEY_PATTERN.matcher(msg);
                while (m.find()) {
                    keys.add(m.group(1));
                }
            }
            return new ArrayList<>(keys);
        }
    }

    /**
     * Retrieves (and caches) the default branch name for the given repo.
     */
    public String getDefaultBranch(String owner, String repo) throws IOException {
        String key = owner + "/" + repo;
        if (branchCache.containsKey(key)) return branchCache.get(key);

        String url = String.format("%s/repos/%s/%s", API_URL, owner, repo);
        Request req = new Request.Builder()
                .url(url)
                .header("Authorization", "token " + authToken)
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "MantiMetricsApp")    // ← obbligatorio
                .build();

        try (Response resp = client.newCall(req).execute()) {
            if (!resp.isSuccessful()) {
                throw new IOException("GitHub API error fetching repo info: HTTP " + resp.code());
            }
            assert resp.body() != null;
            JsonNode root = mapper.readTree(resp.body().string());
            String branch = root.path("default_branch").asText();
            branchCache.put(key, branch);
            return branch;
        }
    }

    /**
     * Returns all paths to .java files under any src/main/java subtree of the repo,
     * using the default branch.
     */
    public List<String> listJavaFiles(String owner, String repo) throws IOException {
        String branch = getDefaultBranch(owner, repo);
        String url = String.format("%s/repos/%s/%s/git/trees/%s?recursive=1",
                API_URL, owner, repo, branch);
        Request req = new Request.Builder()
                .url(url)
                .header("Authorization", "token " + authToken)
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "MantiMetricsApp")    // ← obbligatorio
                .build();

        try (Response resp = client.newCall(req).execute()) {
            if (!resp.isSuccessful()) {
                throw new IOException("GitHub Trees API error: HTTP " + resp.code());
            }
            assert resp.body() != null;
            JsonNode tree = mapper.readTree(resp.body().string()).path("tree");
            List<String> javaFiles = new ArrayList<>();
            for (JsonNode entry : tree) {
                String type = entry.get("type").asText();
                String path = entry.get("path").asText();
                if ("blob".equals(type)
                        && path.endsWith(".java")
                        && path.contains("src/main/java")) {
                    javaFiles.add(path);
                }
            }
            return javaFiles;
        }
    }

    /**
     * Downloads the raw content of a file using the cached default branch.
     */
    public String fetchFileContent(String owner, String repo, String filePath) throws IOException {
        String branch = getDefaultBranch(owner, repo);
        String rawUrl = String.format("%s/%s/%s/%s/%s",
                RAW_URL, owner, repo, branch, filePath);

        Request req = new Request.Builder()
                .url(rawUrl)
                .header("Authorization", "token " + authToken)
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "MantiMetricsApp")    // ← obbligatorio
                .build();

        try (Response resp = client.newCall(req).execute()) {
            if (!resp.isSuccessful()) {
                throw new IOException("GitHub Raw URL error fetching " +
                        filePath + ": HTTP " + resp.code());
            }
            assert resp.body() != null;
            return resp.body().string();
        }
    }
}