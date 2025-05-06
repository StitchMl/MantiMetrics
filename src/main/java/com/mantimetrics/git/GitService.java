package com.mantimetrics.git;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Browse and download Java files from GitHub without cloning the repo.
 * Rate‑limited to 5000 requests/hour using a JDK‑only token bucket.
 * Provides downloadAndUnzipRepo() to download ONE ZIP of the entire ref.
 */
public class GitService {
    private static final Logger logger = LoggerFactory.getLogger(GitService.class);

    private static final String API_URL = "https://api.github.com";
    private static final String CODELOAD_URL = "https://codeload.github.com";

    // token bucket
    private static final int MAX_PERMITS = 5000;
    private final Semaphore ratePermits = new Semaphore(MAX_PERMITS, true);

    private final OkHttpClient client;
    private final ObjectMapper mapper = new ObjectMapper();
    private final String authToken;
    private final Map<String, String> branchCache = new ConcurrentHashMap<>();
    private static final Pattern JIRA_KEY_PATTERN = Pattern.compile("([A-Z]+-\\d+)");

    public GitService(String pat) {
        this.authToken = pat;
        this.client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();

        // hourly refills of up to 5000 permits
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(() -> {
            int toRelease = 5000 - ratePermits.availablePermits();
            if (toRelease > 0) {
                ratePermits.release(toRelease);
                logger.info("Refilled {} permits, available={}", toRelease, ratePermits.availablePermits());
            }
        }, 1, 1, TimeUnit.HOURS);
    }

    private void acquirePermit() {
        ratePermits.acquireUninterruptibly();
    }

    private Response exec(Request req) throws IOException {
        acquirePermit();
        return client.newCall(req).execute();
    }

    private Response executeWithRateLimit(Request req) throws IOException {
        acquirePermit();
        return client.newCall(req).execute();
    }

    private Request build(String url) {
        return new Request.Builder()
                .url(url)
                .header("Authorization", "token " + authToken)
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "MantiMetricsApp")
                .build();
    }

    /**
     * It downloads in ZIP ALL the ref (branch/tag/sha), extracts it into a temp dir and returns the Path.
     */
    public Path downloadAndUnzipRepo(String owner, String repo, String ref) throws IOException {
        String zipUrl = String.format("%s/%s/%s/zip/%s", CODELOAD_URL, owner, repo, URLEncoder.encode(ref, StandardCharsets.UTF_8));
        logger.info("Downloading ZIP of {}/{}@{}…", owner, repo, ref);

        acquirePermit();
        Request req = new Request.Builder().url(zipUrl).build();
        try (Response resp = client.newCall(req).execute()) {
            if (!resp.isSuccessful()) {
                throw new IOException("Error downloading zip: HTTP " + resp.code());
            }
            Path secureTempDir = Paths.get("/path/to/secure/temp");
            Files.createDirectories(secureTempDir);
            Path tmpDir = Files.createTempDirectory(secureTempDir, "mantimetrics-" + repo + "-" + ref + "-");
            assert resp.body() != null;
            try (InputStream in = resp.body().byteStream();
                 ZipInputStream zipIn = new ZipInputStream(in)) {
                ZipEntry entry;
                while ((entry = zipIn.getNextEntry()) != null) {
                    Path outPath = tmpDir.resolve(entry.getName()).normalize();

                    // Prevention of vulnerability Zip Slip
                    if (!outPath.startsWith(tmpDir)) {
                        throw new IOException("Entry is outside of the target dir: " + entry.getName());
                    }

                    if (entry.isDirectory()) {
                        Files.createDirectories(outPath);
                    } else {
                        Files.createDirectories(outPath.getParent());
                        try (OutputStream out = Files.newOutputStream(outPath)) {
                            byte[] buf = new byte[4096];
                            int len;
                            while ((len = zipIn.read(buf)) > 0) {
                                out.write(buf, 0, len);
                            }
                        }
                    }
                    zipIn.closeEntry();
                }
            }
            logger.info("Unzipped to {}", tmpDir);
            return tmpDir;
        }
    }

    public List<String> listTags(String owner, String repo) throws IOException {
        String url = String.format("%s/repos/%s/%s/tags", API_URL, owner, repo);
        logger.info("Listing tags for {}/{}", owner, repo);
        try (Response resp = executeWithRateLimit(build(url))) {
            if (!resp.isSuccessful()) {
                throw new IOException("GitHub API error listing tags: HTTP " + resp.code());
            }
            assert resp.body() != null;
            JsonNode arr = mapper.readTree(resp.body().string());
            List<String> tags = new ArrayList<>();
            for (JsonNode n : arr) {
                String name = n.path("name").asText(null);
                if (name != null) tags.add(name);
            }
            return tags;
        }
    }

    public String getDefaultBranch(String owner, String repo) throws IOException {
        String key = owner + "/" + repo;
        if (branchCache.containsKey(key)) {
            return branchCache.get(key);
        }
        String url = String.format("%s/repos/%s/%s", API_URL, owner, repo);
        try (Response resp = executeWithRateLimit(build(url))) {
            if (!resp.isSuccessful()) {
                throw new IOException("GitHub API error fetching repo info: HTTP " + resp.code());
            }
            assert resp.body() != null;
            String branch = mapper.readTree(resp.body().string())
                    .path("default_branch").asText();
            branchCache.put(key, branch);
            return branch;
        }
    }

    public String getLatestCommitSha(String owner, String repo) throws IOException {
        String branch = getDefaultBranch(owner, repo);
        String url = String.format("%s/repos/%s/%s/commits/%s", API_URL, owner, repo, branch);
        try (Response resp = executeWithRateLimit(build(url))) {
            if (!resp.isSuccessful()) {
                throw new IOException("GitHub API error fetching latest commit: HTTP " + resp.code());
            }
            assert resp.body() != null;
            return mapper.readTree(resp.body().string())
                    .path("sha").asText();
        }
    }

    /**
     * New method: retrieves *all* commits from the branch and builds
     * in memory the file→list map of JIRA keys.
     */
    public Map<String,List<String>> getFileToIssueKeysMap(String owner, String repo) throws IOException {
        String branch = getDefaultBranch(owner, repo);
        String baseUrl = API_URL + "/repos/" + owner + "/" + repo + "/commits?sha="
                + URLEncoder.encode(branch, StandardCharsets.UTF_8)
                + "&per_page=100";
        List<JsonNode> commits = new ArrayList<>();
        int page = 1;
        // 1) paginazione dei commit
        while (true) {
            String paged = baseUrl + "&page=" + page;
            try (Response r = exec(build(paged))) {
                if (!r.isSuccessful()) break;
                assert r.body() != null;
                JsonNode arr = mapper.readTree(r.body().string());
                if (!arr.isArray() || arr.isEmpty()) break;
                arr.forEach(commits::add);
            }
            page++;
        }

        Map<String,List<String>> fileMap = new HashMap<>();
        // 2) for each commit containing JIRA-key ...
        for (JsonNode c : commits) {
            String sha = c.path("sha").asText();
            String msg = c.path("commit").path("message").asText("");
            List<String> keys = new ArrayList<>();
            Matcher m = JIRA_KEY_PATTERN.matcher(msg);
            while (m.find()) keys.add(m.group(1));
            if (keys.isEmpty()) continue;

            // 3) Download the list of files touched by this commit
            String url = API_URL + "/repos/" + owner + "/" + repo + "/commits/" + sha;
            try (Response r2 = exec(build(url))) {
                if (!r2.isSuccessful()) continue;
                assert r2.body() != null;
                JsonNode files = mapper.readTree(r2.body().string()).path("files");
                for (JsonNode f : files) {
                    String fn = f.path("filename").asText(null);
                    if (fn!=null && fn.endsWith(".java")) {
                        fileMap.computeIfAbsent(fn, k->new ArrayList<>()).addAll(keys);
                    }
                }
            }
        }
        logger.info("Built file→issueKeys map (entries={})", fileMap.size());
        return fileMap;
    }
}