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
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Browse and download Java files from GitHub without cloning the repo.
 * Rate‑limited to 5000 requests/hour using a JDK-only token bucket.
 */
public class GitService {
    private static final Logger logger = LoggerFactory.getLogger(GitService.class);

    private static final String API_URL = "https://api.github.com";
    private static final String RAW_URL = "https://raw.githubusercontent.com";

    // ---- Rate limiter: token bucket ----
    private static final int MAX_PERMITS = 5000;
    private final Semaphore ratePermits = new Semaphore(MAX_PERMITS, true);

    // ---- HTTP client and JSON mapper ----
    private final OkHttpClient client;
    private final ObjectMapper mapper = new ObjectMapper();
    private final String authToken;

    // ---- Cache & patterns ----
    private final Map<String, String> branchCache = new ConcurrentHashMap<>();
    private static final Pattern JIRA_KEY_PATTERN = Pattern.compile("([A-Z]+-\\d+)");

    public GitService(String pat) {
        this.authToken = pat;
        this.client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();

        // Ogni ora, ricarica i permessi fino a MAX_PERMITS
        ScheduledExecutorService refillScheduler = Executors.newSingleThreadScheduledExecutor();
        refillScheduler.scheduleAtFixedRate(() -> {
            int toRelease = MAX_PERMITS - ratePermits.availablePermits();
            if (toRelease > 0) {
                ratePermits.release(toRelease);
                logger.info("Refilled {} permits (disponibili: {})",
                        toRelease, ratePermits.availablePermits());
            }
        }, 1, 1, TimeUnit.HOURS);

        logger.info("GitService initialized—max {} requests per hour", MAX_PERMITS);
    }

    /** It acquires a call permission, blocking if exhausted. */
    private void acquirePermit() {
        ratePermits.acquireUninterruptibly();
    }

    /** Executes the OKHttp request respecting the rate-limit. */
    private Response executeWithRateLimit(Request req) throws IOException {
        acquirePermit();
        return client.newCall(req).execute();
    }

    /** Constructs a request with basic auth and headers. */
    private Request newRequest(String url) {
        return new Request.Builder()
                .url(url)
                .header("Authorization", "token " + authToken)
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "MantiMetricsApp")
                .build();
    }

    public List<String> listTags(String owner, String repo) throws IOException {
        logger.info("Listing tags for {}/{}", owner, repo);
        String url = String.format("%s/repos/%s/%s/tags", API_URL, owner, repo);
        try (Response resp = executeWithRateLimit(newRequest(url))) {
            if (!resp.isSuccessful()) {
                throw new IOException("GitHub API error listing tags: HTTP " + resp.code());
            }
            assert resp.body() != null;
            JsonNode array = mapper.readTree(resp.body().string());
            List<String> tags = new ArrayList<>();
            for (JsonNode tagNode : array) {
                String name = tagNode.path("name").asText(null);
                if (name != null) tags.add(name);
            }
            logger.info("Found {} tags for {}/{}", tags.size(), owner, repo);
            return tags;
        }
    }

    public String getDefaultBranch(String owner, String repo) throws IOException {
        String cacheKey = owner + "/" + repo;
        String cached = branchCache.get(cacheKey);
        if (cached != null) {
            logger.debug("Default branch for {}/{} from cache: {}", owner, repo, cached);
            return cached;
        }

        String url = String.format("%s/repos/%s/%s", API_URL, owner, repo);
        try (Response resp = executeWithRateLimit(newRequest(url))) {
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
        try (Response resp = executeWithRateLimit(newRequest(url))) {
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
            logger.debug("Found {} Java files under src/main/java in {}/{}@{}",
                    javaFiles.size(), owner, repo, ref);
            return javaFiles;
        }
    }

    public String fetchFileContent(String owner, String repo, String ref, String filePath)
            throws IOException {
        logger.trace("Fetching content of {}/{}@{}: {}", owner, repo, ref, filePath);
        String rawUrl = String.format("%s/%s/%s/%s/%s",
                RAW_URL, owner, repo, ref, filePath);
        try (Response resp = executeWithRateLimit(newRequest(rawUrl))) {
            if (!resp.isSuccessful()) {
                throw new IOException("GitHub Raw URL error fetching " + filePath
                        + ": HTTP " + resp.code());
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
        try (Response resp = executeWithRateLimit(newRequest(url))) {
            if (!resp.isSuccessful()) {
                throw new IOException("GitHub API error fetching latest commit: HTTP " + resp.code());
            }
            assert resp.body() != null;
            JsonNode commit = mapper.readTree(resp.body().string());
            String sha = commit.path("sha").asText();
            logger.debug("Latest commit SHA: {}", sha);
            return sha;
        }
    }

    public List<String> getIssueKeysForFile(String owner, String repo, String filePath)
            throws IOException {
        logger.debug("Fetching JIRA keys for file {}/{}: {}", owner, repo, filePath);
        String branch = getDefaultBranch(owner, repo);
        String url = String.format("%s/repos/%s/%s/commits?sha=%s&path=%s",
                API_URL, owner, repo,
                URLEncoder.encode(branch, StandardCharsets.UTF_8),
                URLEncoder.encode(filePath, StandardCharsets.UTF_8));
        try (Response resp = executeWithRateLimit(newRequest(url))) {
            if (!resp.isSuccessful()) {
                throw new IOException("GitHub Commits API error: HTTP " + resp.code());
            }
            assert resp.body() != null;
            JsonNode commits = mapper.readTree(resp.body().string());
            Set<String> keys = new HashSet<>();
            for (JsonNode c : commits) {
                String msg = c.path("commit").path("message").asText("");
                Matcher m = JIRA_KEY_PATTERN.matcher(msg);
                while (m.find()) keys.add(m.group(1));
            }
            logger.debug("Found {} JIRA keys in {}", keys.size(), filePath);
            return new ArrayList<>(keys);
        }
    }
}