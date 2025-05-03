package com.mantimetrics.git;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Browse and download Java files from GitHub without cloning the repo.
 * Use the Git Trees API {{GET /repos/:owner/:repo/git/trees/:branch?recursive=1}}: contentReference[oaicite:0]{index=0}
 * and only filters .java blobs under src/main/java, excluding archetype resources.
 */
public class GitService {
    private static final String API_URL = "https://api.github.com";
    private static final String RAW_URL = "https://raw.githubusercontent.com";
    private final OkHttpClient client = new OkHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();
    private final String authToken;
    private final Map<String,String> branchCache = new HashMap<>();
    private static final Pattern JIRA_KEY_PATTERN = Pattern.compile("([A-Z]+-\\d+)");

    public GitService(String pat) {
        this.authToken = pat;
    }

    /** It gets (and caches) the default branch to avoid repeated calls. */
    public String getDefaultBranch(String owner, String repo) throws IOException {
        String key = owner+"/"+repo;
        if (branchCache.containsKey(key)) return branchCache.get(key);

        String url = String.format("%s/repos/%s/%s", API_URL, owner, repo);
        Request req = new Request.Builder()
                .url(url)
                .header("Authorization", "token " + authToken)
                .header("Accept", "application/vnd.github.v3+json")
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
     * Recursively lists all .java blobs under src/main/java,
     * excluding any path containing /src/main/resources/: contentReference[oaicite:1]{index=1}.
     */
    public List<String> listJavaFiles(String owner, String repo) throws IOException {
        String branch = getDefaultBranch(owner, repo);
        String url = String.format("%s/repos/%s/%s/git/trees/%s?recursive=1",
                API_URL, owner, repo, branch);

        Request req = new Request.Builder()
                .url(url)
                .header("Authorization", "token " + authToken)
                .header("Accept", "application/vnd.github.v3+json")
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
                // Includi SOLO i .java sotto src/main/java e NON in src/main/resources
                if ("blob".equals(type)
                        && path.endsWith(".java")
                        && path.contains("src/main/java")
                        && !path.contains("/src/main/resources/")) {
                    javaFiles.add(path);
                }
            }
            return javaFiles;
        }
    }

    /**
     * Download the raw content of a file, using raw.githubusercontent.com
     * (without authentication, for public repos): contentReference[oaicite:2]{index=2}.
     */
    public String fetchFileContent(String owner, String repo, String filePath) throws IOException {
        String branch = getDefaultBranch(owner, repo);
        String rawUrl = String.format("%s/%s/%s/%s/%s",
                RAW_URL, owner, repo, branch, filePath);
        Request req = new Request.Builder().url(rawUrl).build();
        try (Response resp = client.newCall(req).execute()) {
            if (!resp.isSuccessful()) {
                throw new IOException("Raw content error: HTTP " + resp.code());
            }
            assert resp.body() != null;
            return resp.body().string();
        }
    }
}