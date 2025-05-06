package com.mantimetrics.git;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Browse and download Java files from GitHub without cloning the repo.
 * Uses GitHub REST API v3 and raw.githubusercontent.com.
 */
public class GitService {
    private static final Logger logger = LoggerFactory.getLogger(GitService.class);

    private static final String API_URL = "https://api.github.com";
    private static final String RAW_URL = "https://raw.githubusercontent.com";
    private final OkHttpClient client;
    private final ObjectMapper mapper = new ObjectMapper();
    private final String authToken;
    private final Map<String, String> branchCache = new HashMap<>();
    private static final Pattern JIRA_KEY_PATTERN = Pattern.compile("([A-Z]+-\\d+)");

    public GitService(String pat) {
        this.authToken = pat;
        this.client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
        logger.info("GitService initialized with provided PAT");
    }

    private Request newRequestBuild(String url) {
        return new Request.Builder()
                .url(url)
                .header("Authorization", "token " + authToken)
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "MantiMetricsApp")    // obbligatorio per GitHub API
                .build();
    }

    public List<String> getIssueKeysForFile(String owner, String repo, String filePath) throws IOException {
        logger.debug("Fetching JIRA keys for file {}/{}: {}", owner, repo, filePath);
        String branch = getDefaultBranch(owner, repo);
        String url = String.format("%s/repos/%s/%s/commits?sha=%s&path=%s",
                API_URL,
                owner,
                repo,
                URLEncoder.encode(branch, StandardCharsets.UTF_8),
                URLEncoder.encode(filePath, StandardCharsets.UTF_8));

        Request req = newRequestBuild(url);
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
            logger.debug("Found {} JIRA keys in commit history of {}", keys.size(), filePath);
            return new ArrayList<>(keys);
        }
    }

    public String getDefaultBranch(String owner, String repo) throws IOException {
        String cacheKey = owner + "/" + repo;
        if (branchCache.containsKey(cacheKey)) {
            logger.debug("Default branch for {}/{} retrieved from cache: {}", owner, repo, branchCache.get(cacheKey));
            return branchCache.get(cacheKey);
        }

        String url = String.format("%s/repos/%s/%s", API_URL, owner, repo);
        Request req = newRequestBuild(url);
        try (Response resp = client.newCall(req).execute()) {
            if (!resp.isSuccessful()) {
                throw new IOException("GitHub API error fetching repo info: HTTP " + resp.code());
            }
            assert resp.body() != null;
            JsonNode root = mapper.readTree(resp.body().string());
            String branch = root.path("default_branch").asText();
            branchCache.put(cacheKey, branch);
            logger.debug("Default branch for {}/{} is {}", owner, repo, branch);
            return branch;
        }
    }

    public List<String> listJavaFiles(String owner, String repo, String ref) throws IOException {
        logger.debug("Listing .java files in {}/{}@{}", owner, repo, ref);
        String url = String.format("%s/repos/%s/%s/git/trees/%s?recursive=1",
                API_URL, owner, repo, ref);
        Request req = newRequestBuild(url);
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
            logger.debug("Found {} Java files under src/main/java in {}/{}@{}", javaFiles.size(), owner, repo, ref);
            return javaFiles;
        }
    }

    public String fetchFileContent(String owner, String repo, String ref, String filePath) throws IOException {
        logger.trace("Fetching raw content of {}/{}@{}: {}", owner, repo, ref, filePath);
        String rawUrl = String.format("%s/%s/%s/%s/%s",
                RAW_URL, owner, repo, ref, filePath);
        Request req = newRequestBuild(rawUrl);
        try (Response resp = client.newCall(req).execute()) {
            if (!resp.isSuccessful()) {
                throw new IOException("GitHub Raw URL error fetching " + filePath + ": HTTP " + resp.code());
            }
            assert resp.body() != null;
            String content = resp.body().string();
            logger.trace("Fetched {} bytes from {}", content.length(), filePath);
            return content;
        }
    }

    public String getLatestCommitSha(String owner, String repo) throws IOException {
        logger.debug("Retrieving latest commit SHA for {}/{}", owner, repo);
        String branch = getDefaultBranch(owner, repo);
        String url = String.format("%s/repos/%s/%s/commits/%s", API_URL, owner, repo, branch);
        Request req = newRequestBuild(url);
        try (Response resp = client.newCall(req).execute()) {
            if (!resp.isSuccessful()) {
                throw new IOException("GitHub API error fetching latest commit: HTTP " + resp.code());
            }
            assert resp.body() != null;
            JsonNode commit = mapper.readTree(resp.body().string());
            String sha = commit.path("sha").asText();
            logger.debug("Latest commit SHA for {}/{} is {}", owner, repo, sha);
            return sha;
        }
    }

    public List<String> listTags(String owner, String repo) throws IOException {
        logger.info("Listing tags for {}/{}", owner, repo);
        String url = String.format("%s/repos/%s/%s/tags", API_URL, owner, repo);
        Request req = newRequestBuild(url);
        try (Response resp = client.newCall(req).execute()) {
            if (!resp.isSuccessful()) {
                throw new IOException("GitHub API error listing tags: HTTP " + resp.code());
            }
            assert resp.body() != null;
            JsonNode array = mapper.readTree(resp.body().string());
            List<String> tags = new ArrayList<>();
            for (JsonNode tagNode : array) {
                String name = tagNode.path("name").asText(null);
                if (name != null) {
                    tags.add(name);
                }
            }
            logger.info("Found {} tags for {}/{}", tags.size(), owner, repo);
            return tags;
        }
    }
}