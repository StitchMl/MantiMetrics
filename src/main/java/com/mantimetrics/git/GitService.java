package com.mantimetrics.git;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import java.io.IOException;
import java.util.concurrent.CopyOnWriteArrayList;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.util.io.DisabledOutputStream;

public class GitService {
    private static final Logger logger = LoggerFactory.getLogger(GitService.class);
    private static final String CODELOAD_URL = "https://codeload.github.com";
    private static final int MAX_PERMITS = 5000;

    private final ObjectMapper mapper = new ObjectMapper();
    private final Semaphore ratePermits = new Semaphore(MAX_PERMITS, true);
    /** Keeps track of all temporary folders created */
    private final List<Path> tempDirs = new CopyOnWriteArrayList<>();
    private static final String PRIV_DIR_NAME = ".mantimetrics-tmp";

    // ---------- constants --------------------------------------------------
    private static final Logger  log          = LoggerFactory.getLogger(GitService.class);
    private static final String  API          = "https://api.github.com";
    private static final int     MAX_RETRIES  = 5;

    // ---------- state ------------------------------------------------------
    private final OkHttpClient client;
    private final String         token;
    private static final Pattern JIRA_RE = Pattern.compile("\\b([A-Z][A-Z0-9]+-\\d+)\\b");

    /* ───────────────────────────────── cache in-memory ───────────────────────── */
    private final Map<String, Map<String, List<String>>> projectCache = new HashMap<>();

    public GitService(String pat) {
        this.token = pat;
        this.client = new OkHttpClient.Builder()
                .connectTimeout(java.time.Duration.ofSeconds(30))
                .build();
    }

    private void acquirePermit() {
        ratePermits.acquireUninterruptibly();
    }

    private Request build(String url) {
        return new Request.Builder()
                .url(url)
                .header("Authorization", "token " + token)
                .header("Accept", "application/vnd.github.v3+json")
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
            Path tempRoot = Files.createTempDirectory(
                    privateSandbox(),
                    "mantimetrics-"+repo+"-"+ref+"-");
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

    // ----------------------------------------------------------------------
    // public helpers
    // ----------------------------------------------------------------------

    /** full, paginated list of tags – with automatic retry on 403 RateLimit. */
    public List<String> listTags(String owner, String repo) throws IOException, InterruptedException {
        List<String> out = new ArrayList<>();
        int page = 1;
        while (true) {
            String u = String.format("%s/repos/%s/%s/tags?per_page=100&page=%d",
                    API, owner, repo, page);
            JsonNode arr = getWithRetry(u);
            if (!arr.isArray() || arr.isEmpty()) break;
            arr.forEach(n -> Optional.ofNullable(n.path("name").asText(null))
                    .ifPresent(out::add));
            page++;
        }
        log.info("Found {} total tags for {}/{}", out.size(), owner, repo);
        return out;
    }

    /* ───────────────────────────────── public API ────────────────────────────── */

    /**
     * @return mappa immutabile file.java → lista JIRA-key
     */
    public Map<String,List<String>> getFileToIssueKeysMap(
            String owner,
            String repo,
            String branch) throws FileKeyMappingException {
        String key = owner + "/" + repo;
        if (projectCache.containsKey(key)) {
            return projectCache.get(key);
        }

        try {
            Path dir = Files.createTempDirectory("mantimetrics-git-" + repo + "-");
            tempDirs.add(dir);
            log.info("Cloning https://github.com/{}/{} (full) → {}", owner, repo, dir);

            Git.cloneRepository()
                    .setURI("https://github.com/" + owner + "/" + repo + ".git")
                    .setDirectory(dir.toFile())
                    .setBranch(branch)              // default = master / main
                    .call()
                    .close();

            /* ---------- JGit walk & diff ---------- */
            Repository repository = new FileRepositoryBuilder()
                    .setGitDir(dir.resolve(".git").toFile())
                    .build();

            Map<String, List<String>> fileMap = new HashMap<>();
            try (RevWalk walk = new RevWalk(repository);
                 DiffFormatter df = new DiffFormatter(DisabledOutputStream.INSTANCE)) {

                ObjectId head = repository.resolve(branch);
                walk.markStart(walk.parseCommit(head));
                df.setRepository(repository);

                for (RevCommit c : walk) {
                    List<String> keys = extractKeys(c.getFullMessage());
                    if (keys.isEmpty()) continue;

                    RevCommit parent = c.getParentCount() > 0
                            ? walk.parseCommit(c.getParent(0).getId()) : null;

                    for (DiffEntry d : df.scan(parent, c)) {
                        String p = d.getNewPath();
                        if (p.endsWith(".java")) {
                            fileMap.computeIfAbsent(p, str -> new ArrayList<>())
                                    .addAll(keys);
                        }
                    }
                }
            } finally {
                repository.close();
            }

            log.info("Built file→JIRA map for {}: {} java files", key, fileMap.size());
            Map<String, List<String>> unmodifiable = Collections.unmodifiableMap(fileMap);
            projectCache.put(key, unmodifiable);
            return unmodifiable;
        } catch (IOException | GitAPIException e) {
            throw new FileKeyMappingException("Failed to build file→issue map for "
                    + owner + "/" + repo + "@" + branch, e);
        }
    }

    /** Regex helper */
    private static List<String> extractKeys(String msg) {
        List<String> out = new ArrayList<>();
        Matcher m = JIRA_RE.matcher(msg);
        while (m.find()) out.add(m.group(1));
        return out;
    }

    // ----------------------------------------------------------------------
    // private – generic REST call with rate-limit handling
    // ----------------------------------------------------------------------

    private JsonNode getWithRetry(String url) throws IOException, InterruptedException {

        for (int attempts = 0; attempts < MAX_RETRIES; attempts++) {

            try (Response resp = client.newCall(build(url)).execute()) {
                if (resp.isSuccessful() && resp.body() != null) {
                    return mapper.readTree(resp.body().string());
                }

                /* ---------------- rate-limit handling ---------------- */
                if ((resp.code() == 403 || resp.code() == 429)) {

                    long sleepMs = computeBackOff(resp, attempts);
                    log.warn("Rate-limit hit (HTTP {}), retry {}/{} in {} s – {}",
                            resp.code(), attempts, MAX_RETRIES, sleepMs / 1000, url);

                    TimeUnit.MILLISECONDS.sleep(sleepMs);
                    continue;
                }

                /* ----- other HTTP errors: propagate immediately ------ */
                throw new IOException("GitHub HTTP " + resp.code() + " – " + url);
            }
        }
        throw new IOException("Retries exhausted (" + MAX_RETRIES + ") for " + url);
    }

    private static long computeBackOff(Response r, int attempt) {
        // 1) Honor explicit Retry-After first
        String ra = r.header("Retry-After");
        if (ra != null) {
            return (Long.parseLong(ra) + 1) * 1_000L;
        }
        // 2) Primary rate-limit: wait until X-RateLimit-Reset
        String remain = r.header("X-RateLimit-Remaining");
        String reset  = r.header("X-RateLimit-Reset");
        if ("0".equals(remain) && reset != null) {
            long resetMillis = Long.parseLong(reset) * 1_000L;
            return Math.max(resetMillis - System.currentTimeMillis(), 5_000);
        }
        // 3) Fallback: exponential (3 s, 6 s, 12 s, …)
        return (long) (3_000L * Math.pow(2, attempt - 1.0));
    }

    /** Return a process-private directory (700 / rwx------) under the user-home. */
    private static Path privateSandbox() throws IOException {
        Path home = Paths.get(System.getProperty("user.home"));      // never world-writable :contentReference[oaicite:0]{index=0}
        Path box  = home.resolve(PRIV_DIR_NAME);
        // create once with 0700; Posix not available on Windows so ignore silently
        if (Files.notExists(box)) {
            try {
                Files.createDirectory(
                        box,
                        PosixFilePermissions.asFileAttribute(
                                PosixFilePermissions.fromString("rwx------")));
            } catch (UnsupportedOperationException e) {
                Files.createDirectory(box);          // fallback (Windows ACLs)
            }
        }
        return box;
    }


    /* ───────────────────────────────── housekeeping ─────────────────────────── */

    public List<Path> getTempDirs() { return List.copyOf(tempDirs); }
}