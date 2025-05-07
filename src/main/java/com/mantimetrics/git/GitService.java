package com.mantimetrics.git;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.util.io.DisabledOutputStream;
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

public class GitService {
    private static final Logger logger = LoggerFactory.getLogger(GitService.class);

    private static final String API_URL     = "https://api.github.com";
    private static final String CODELOAD_URL = "https://codeload.github.com";
    private static final Pattern JIRA_KEY_PATTERN = Pattern.compile("\\b(?>[A-Z]++-\\d++)\\b");
    private static final int MAX_PERMITS = 5000;

    private final OkHttpClient client;
    private final ObjectMapper mapper = new ObjectMapper();
    private final String authToken;
    private final Semaphore ratePermits = new Semaphore(MAX_PERMITS, true);
    private final Map<String, String> branchCache = new ConcurrentHashMap<>();

    public GitService(String pat) {
        this.authToken = pat;
        this.client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();

        // refill token bucket ogni ora
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(() -> {
            int toRelease = MAX_PERMITS - ratePermits.availablePermits();
            if (toRelease > 0) {
                ratePermits.release(toRelease);
                logger.info("Refilled {} permits (available={})", toRelease, ratePermits.availablePermits());
            }
        }, 1, 1, TimeUnit.HOURS);

        logger.info("GitService initialized—max {} requests/hour", MAX_PERMITS);
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
     * Scarica lo ZIP della release (tag/sha), lo estrae in una dir temporanea
     * e ne restituisce il path.
     */
    public Path downloadAndUnzipRepo(String owner, String repo, String ref) throws IOException {
        String zipUrl = String.format("%s/%s/%s/zip/%s",
                CODELOAD_URL, owner, repo, URLEncoder.encode(ref, StandardCharsets.UTF_8));
        logger.info("Downloading ZIP of {}/{}@{} …", owner, repo, ref);

        acquirePermit();
        Request req = new Request.Builder().url(zipUrl).build();
        try (Response resp = client.newCall(req).execute()) {
            if (!resp.isSuccessful()) {
                throw new IOException("Error downloading ZIP: HTTP " + resp.code());
            }
            Path tempRoot = Files.createTempDirectory("mantimetrics-" + repo + "-" + ref + "-");
            assert resp.body() != null;
            try (InputStream in = resp.body().byteStream();
                 ZipInputStream zipIn = new ZipInputStream(in)) {

                ZipEntry entry;
                while ((entry = zipIn.getNextEntry()) != null) {
                    Path out = tempRoot.resolve(entry.getName()).normalize();
                    if (!out.startsWith(tempRoot)) {
                        throw new IOException("ZIP entry outside target dir: " + entry.getName());
                    }
                    if (entry.isDirectory()) {
                        Files.createDirectories(out);
                    } else {
                        Files.createDirectories(out.getParent());
                        try (OutputStream os = Files.newOutputStream(out)) {
                            byte[] buf = new byte[4096];
                            int len;
                            while ((len = zipIn.read(buf)) > 0) {
                                os.write(buf, 0, len);
                            }
                        }
                    }
                    zipIn.closeEntry();
                }
            }
            logger.info("Unzipped to {}", tempRoot);
            return tempRoot;
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
     * Clona localmente il branch/tag in un bare repo e ne costruisce la mappa
     * file → lista di JIRA‑keys (da tutti i commit di quel branch).
     */
    public Map<String,List<String>> getFileToIssueKeysMap(String owner, String repo, String branch) throws Exception {
        // 1) clone
        Path repoDir = cloneRepository(owner, repo, branch);
        logger.info("Cloned {}@{} into {}", owner, branch, repoDir);

        // 2) prepare JGit
        Repository repository = new FileRepositoryBuilder()
                .setGitDir(repoDir.resolve(".git").toFile())
                .build();
        RevWalk walk = new RevWalk(repository);
        ObjectId head = repository.resolve(branch);
        walk.markStart(walk.parseCommit(head));

        // 3) diff formatter
        DiffFormatter df = new DiffFormatter(DisabledOutputStream.INSTANCE);
        df.setRepository(repository);

        Map<String,List<String>> fileMap = new HashMap<>();

        for (RevCommit commit : walk) {
            // estrai JIRA keys
            String msg = commit.getFullMessage();
            Matcher m = JIRA_KEY_PATTERN.matcher(msg);
            List<String> keys = new ArrayList<>();
            while (m.find()) keys.add(m.group());
            if (keys.isEmpty()) continue;

            // diff vs parent
            RevCommit parent = commit.getParentCount()>0
                    ? walk.parseCommit(commit.getParent(0).getId())
                    : null;
            for (DiffEntry de : df.scan(parent, commit)) {
                String path = de.getNewPath();
                if (path.endsWith(".java")) {
                    fileMap.computeIfAbsent(path, k->new ArrayList<>()).addAll(keys);
                }
            }
        }

        // cleanup
        df.close();
        walk.close();
        repository.close();

        logger.info("Built file→JIRA-keys map: {} files", fileMap.size());
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
        logger.info("Cloning https://github.com/{}/{}.git#{} → {}", owner, repo, branch, tmp);
        Git.cloneRepository()
                .setURI("https://github.com/" + owner + "/" + repo + ".git")
                .setDirectory(tmp.toFile())
                .setBranch(branch)
                .setCloneAllBranches(false)
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