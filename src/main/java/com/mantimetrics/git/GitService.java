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
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class GitService {
    private static final Logger logger = LoggerFactory.getLogger(GitService.class);

    private static final String API_URL      = "https://api.github.com";
    private static final String CODELOAD_URL = "https://codeload.github.com";
    private static final Pattern JIRA_KEY_PATTERN = Pattern.compile("\\b(?>[A-Z]++-\\d++)\\b");
    private static final int MAX_PERMITS = 5000;

    private final OkHttpClient client;
    private final ObjectMapper mapper = new ObjectMapper();
    private final String authToken;
    private final Semaphore ratePermits = new Semaphore(MAX_PERMITS, true);
    /** Keeps track of all temporary folders created */
    private final List<Path> tempDirs = new CopyOnWriteArrayList<>();

    public GitService(String pat) {
        this.authToken = pat;
        this.client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();

        // refill token bucket every hour
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(() -> {
            int toRelease = MAX_PERMITS - ratePermits.availablePermits();
            if (toRelease > 0) {
                ratePermits.release(toRelease);
                logger.info("Refilled {} permits (available={})",
                        toRelease, ratePermits.availablePermits());
            }
        }, 1, 1, TimeUnit.HOURS);

        // disable JGit DEBUG logs
        java.util.logging.Logger.getLogger("org.eclipse.jgit").setLevel(java.util.logging.Level.WARNING);

        logger.trace("GitService initialized—max {} requests/hour", MAX_PERMITS);
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

    /** It downloads the ZIP of the release and extracts it into a temporary dir. */
    public Path downloadAndUnzipRepo(String owner, String repo, String ref) throws IOException {
        String zipUrl = String.format("%s/%s/%s/zip/%s",
                CODELOAD_URL,
                owner, repo,
                URLEncoder.encode(ref, StandardCharsets.UTF_8));
        logger.trace("Downloading ZIP of {}/{}@{} …", owner, repo, ref);

        acquirePermit();
        Request req = new Request.Builder().url(zipUrl).build();
        try (Response resp = client.newCall(req).execute()) {
            if (!resp.isSuccessful()) {
                throw new IOException("Error downloading ZIP: HTTP " + resp.code());
            }
            Path tempRoot = Files.createTempDirectory("mantimetrics-" + repo + "-" + ref + "-");
            tempDirs.add(tempRoot);
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
            logger.trace("Unzipped to {}", tempRoot);
            return tempRoot;
        }
    }

    /**
     * List *all* tags (pages of 100) until it finds no more
     * to retrieve even the oldest ones.
     */
    public List<String> listTags(String owner, String repo) throws IOException {
        List<String> tags = new ArrayList<>();
        int page = 1;

        while (true) {
            String url = String.format(
                    API_URL + "/repos/%s/%s/tags?per_page=100&page=%d",
                    owner, repo, page++
            );
            logger.trace("Listing tags page {} for {}/{}", page-1, owner, repo);
            Request req = build(url);
            try (Response resp = exec(req)) {
                if (!resp.isSuccessful() || resp.body() == null) {
                    logger.warn("GitHub API error listing tags: HTTP {}", resp.code());
                    break;
                }
                JsonNode array = mapper.readTree(resp.body().string());
                if (!array.isArray() || array.isEmpty()) {
                    // there are no more pages
                    break;
                }
                for (JsonNode node : array) {
                    String name = node.path("name").asText(null);
                    if (name != null) tags.add(name);
                }
            }
        }

        logger.info("Found {} total tags for {}/{}", tags.size(), owner, repo);
        return tags;
    }

    /**
     * Clone the branch/tag locally and create the map in memory
     * file → list of JIRA keys from commits that have modified it.
     */
    public Map<String,List<String>> getFileToIssueKeysMap(
            String owner, String repo, String branch) throws Exception {

        // 1) clone
        Path repoDir = Files.createTempDirectory("mantimetrics-git-" + repo + "-");
        logger.trace("Cloning in {}", repoDir);
        Git.cloneRepository()
                .setURI("https://github.com/" + owner + "/" + repo + ".git")
                .setDirectory(repoDir.toFile())
                .setBranch(branch)
                .setCloneAllBranches(false)
                .setDepth(1)
                .call()
                .close();

        // 2) prepares JGit
        Repository repository = new FileRepositoryBuilder()
                .setGitDir(repoDir.resolve(".git").toFile())
                .build();
        RevWalk walk = new RevWalk(repository);
        ObjectId head = repository.resolve(branch);
        walk.markStart(walk.parseCommit(head));

        DiffFormatter df = new DiffFormatter(DisabledOutputStream.INSTANCE);
        df.setRepository(repository);

        Map<String,List<String>> fileMap = new HashMap<>();
        for (RevCommit commit : walk) {
            // extract the JIRA keys
            Matcher m = JIRA_KEY_PATTERN.matcher(commit.getFullMessage());
            List<String> keys = new ArrayList<>();
            while (m.find()) keys.add(m.group());
            if (keys.isEmpty()) continue;

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

        // cleanup JGit
        df.close();
        walk.close();
        repository.close();

        // delete the cloned folder
        try (Stream<Path> stream = Files.walk(repoDir)) {
            stream.sorted(Comparator.reverseOrder())
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException e) {
                            // on Windows often pack/.idx is still locked: it only logs to debug
                            logger.warn("Failed to delete {}: {}", p, e.getMessage());
                        }
                    });
        } catch (IOException e) {
            logger.error("Error walking temp dir {}: {}", repoDir, e.getMessage());
        }

        logger.trace("Deleted cloned repo {}", repoDir);

        logger.trace("Built file→JIRA-keys map: {} files", fileMap.size());
        return fileMap;
    }

    /** Returns the list of folders to be cleaned */
    public List<Path> getTempDirs() {
        return List.copyOf(tempDirs);
    }
}