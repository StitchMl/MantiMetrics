package com.mantimetrics.git;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.eclipse.jgit.api.Git;
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

import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.util.io.DisabledOutputStream;

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
    private static final Pattern JIRA_KEY_PATTERN = Pattern.compile("\\b(?>[A-Z]++-\\d++)\\b");

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
        try (Response resp = exec(build(url))) {
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
        try (Response resp = exec(build(url))) {
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
        try (Response resp = exec(build(url))) {
            if (!resp.isSuccessful()) {
                throw new IOException("GitHub API error fetching latest commit: HTTP " + resp.code());
            }
            assert resp.body() != null;
            return mapper.readTree(resp.body().string())
                    .path("sha").asText();
        }
    }

    /**
     * Extracts **once** the map: path file.java → a list of JIRA keys
     * taken from ALL commits of `branch`.
     */
    public Map<String,List<String>> getFileToIssueKeysMap(
            String owner, String repo, String branch) throws Exception {

        // 1) Local cloning
        Path repoDir = cloneRepository(owner, repo, branch);
        logger.info("Cloned {}@{} into {}", owner, branch, repoDir);

        // 2) I prepare JGit
        Repository repository = new FileRepositoryBuilder()
                .setGitDir(repoDir.resolve(".git").toFile())
                .build();
        RevWalk walk = new RevWalk(repository);

        // 3) I point to the last branch commit
        ObjectId headId = repository.resolve(branch);
        walk.markStart(walk.parseCommit(headId));
        logger.info("Last commit SHA: {}", headId.getName());

        // 4) Formatter for diff
        DiffFormatter df = new DiffFormatter(DisabledOutputStream.INSTANCE);
        df.setRepository(repository);
        logger.info("Diff formatter created");

        Map<String,List<String>> fileMap = new HashMap<>();
        Pattern jiraPattern = Pattern.compile("\\b(?>[A-Z]++-\\d++)\\b");

        // 5) Iterate all commit
        logger.info("Iterating commits...");
        for (RevCommit commit : walk) {
            // I extract the JIRA keys
            String msg = commit.getFullMessage();
            Matcher m = jiraPattern.matcher(msg);
            List<String> keys = new ArrayList<>();
            while (m.find()) keys.add(m.group());

            if (keys.isEmpty()) continue;

            // diff against parent (if any)
            RevCommit parent = (commit.getParentCount() > 0)
                    ? walk.parseCommit(commit.getParent(0).getId())
                    : null;

            List<DiffEntry> diffs = df.scan(parent, commit);
            for (DiffEntry de : diffs) {
                String path = de.getNewPath();
                if (path.endsWith(".java")) {
                    fileMap.computeIfAbsent(path, k -> new ArrayList<>())
                            .addAll(keys);
                }
            }
        }
        logger.info("Found {} files with JIRA keys", fileMap.size());

        // 6) cleaning
        df.close();
        walk.close();
        repository.close();
        // (optional) delete the tmp folder: Files.walk(...) ...

        return fileMap;
    }

// --- Helper Methods ---

    private List<JsonNode> fetchAllCommits(String owner, String repo, String branch) throws IOException {
        List<JsonNode> commits = new ArrayList<>();
        String baseUrl = API_URL + "/repos/" + owner + "/" + repo + "/commits?sha="
                + URLEncoder.encode(branch, StandardCharsets.UTF_8)
                + "&per_page=100";

        int page = 1;
        while (true) {
            String paged = baseUrl + "&page=" + page++;
            try (Response r = exec(build(paged))) {
                // parse body only once
                assert r.body() != null;
                JsonNode arr = mapper.readTree(r.body().string());
                // combine both array checks here
                if (!r.isSuccessful() && (!arr.isArray() || arr.isEmpty())) {
                    break; // single break for all exit conditions
                }
                arr.forEach(commits::add);
            }
        }
        return commits;
    }

    /**
     * Clone locally **all** the `branch` history into a bare tempdir,
     * returning the Path of the cloned folder.
     */
    private Path cloneRepository(String owner, String repo, String branch) throws Exception {
        Path tmp = Files.createTempDirectory("mantimetrics-git-" + repo + "-");
        logger.info("Cloning {}@{} into {}", owner, branch, tmp);
        logger.debug("https://github.com/{}/{}.git", owner, repo);
        Git.cloneRepository()
                .setURI("https://github.com/" + owner + "/" + repo + ".git")
                .setDirectory(tmp.toFile())
                .setBranch(branch)
                .setDepth(0)   // 0 = tutta la history
                .call()
                .close();
        return tmp;
    }

    private List<String> extractJiraKeys(JsonNode commit) {
        List<String> keys = new ArrayList<>();
        String msg = commit.path("commit").path("message").asText("");
        Matcher m = JIRA_KEY_PATTERN.matcher(msg);
        while (m.find()) keys.add(m.group(1));
        return keys;
    }

    private List<String> getJavaFilesInCommit(String owner, String repo, String sha) throws IOException {
        String url = API_URL + "/repos/" + owner + "/" + repo + "/commits/" + sha;
        try (Response r = exec(build(url))) {
            if (!r.isSuccessful() || r.body() == null) return List.of();
            JsonNode files = mapper.readTree(r.body().string()).path("files");
            List<String> javaFiles = new ArrayList<>();
            for (JsonNode f : files) {
                String filename = f.path("filename").asText(null);
                if (filename != null && filename.endsWith(".java")) {
                    javaFiles.add(filename);
                }
            }
            return javaFiles;
        }
    }
}