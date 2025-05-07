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
import java.nio.file.attribute.PosixFilePermissions;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import java.util.concurrent.*;

/** Git-utility layer: rate-limit aware, caches maps in-memory, produces temp-dirs safely. */
public final class GitService {
    /* cache for one-time look-ups */
    private final Map<String,String>   defaultBranchCache = new ConcurrentHashMap<>();
    private final Map<String,Map<String,List<String>>> projectCache = new ConcurrentHashMap<>();
    /* ---------- constants ---------- */
    private static final Logger  LOG = LoggerFactory.getLogger(GitService.class);
    private static final String  API = "https://api.github.com";
    private static final int     MAX_RETRIES = 5;
    /** safe, no-back-tracking regex (atomic groups and possessive quantifiers) */
    private static final Pattern JIRA_RE = Pattern.compile("\\b(?>[A-Z][A-Z0-9]++-\\d++)\\b");
    private static final String PRIV_DIR_NAME = ".mantimetrics-tmp";

    /* ---------- state ---------- */
    private final OkHttpClient client;
    private final ObjectMapper json = new ObjectMapper();
    private final String token;
    private final List<Path> tmpDirs = new CopyOnWriteArrayList<>();
    /** cache project → branch → map(file→keys) */
    private static final Logger logger = LoggerFactory.getLogger(GitService.class);
    private static final String CODELOAD_URL = "https://codeload.github.com";
    private static final int MAX_PERMITS = 5000;

    private final Semaphore ratePermits = new Semaphore(MAX_PERMITS, true);

    /* ---------- ctor ---------- */
    public GitService(String pat) {
        this.token = pat;
        this.client = new OkHttpClient.Builder()
                .connectTimeout(java.time.Duration.ofSeconds(30))
                .build();
    }

    private void acquirePermit() {
        ratePermits.acquireUninterruptibly();
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
            tmpDirs.add(tempRoot);
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

    /* ====================================================================== */
    /* public helpers                                                         */
    /* ====================================================================== */

    /** Return the default branch as declared by GitHub (`main`, `master`, …). */
    public String getDefaultBranch(String owner,String repo) {

        return defaultBranchCache.computeIfAbsent(owner+'/'+repo, k -> {
            try {
                JsonNode n = getWithRetry(API+"/repos/"+owner+"/"+repo);
                return n.path("default_branch").asText("master");
            } catch(Exception e){ throw new UncheckedIOException(new IOException(e)); }
        });
    }

    /** List *all* tags, paging 100 × 100 until exhausted. */
    public List<String> listTags(String owner, String repo)
            throws IOException, InterruptedException {

        List<String> out = new ArrayList<>();
        for (int page = 1; ; page++) {
            String u = String.format(
                    "%s/repos/%s/%s/tags?per_page=100&page=%d", API, owner, repo, page);
            JsonNode arr = getWithRetry(u);
            if (!arr.isArray() || arr.isEmpty()) break;
            arr.forEach(x -> Optional.ofNullable(x.path("name").asText(null))
                    .ifPresent(out::add));
        }
        LOG.info("Found {} tags for {}/{}", out.size(), owner, repo);
        return out;
    }

    /**
     * Lazily builds (and then reuses) the file → JIRA-key map for an entire project.
     * <p>⚠️ The <strong>branch</strong> parameter is only used on the first call;
     * later calls for the same {@code owner/repo} will return the cached map.</p>
     */
    public Map<String,List<String>> getFileToIssueKeysMap(String owner,
                                                          String repo,
                                                          String branch)
            throws FileKeyMappingException {

        String cacheKey = owner + "/" + repo;

        try {
            /* -- computeIfAbsent guarantees the value is *read* after it is created -- */
            return projectCache.computeIfAbsent(cacheKey, k -> {
                try {
                    /* ---------- clone once (shallow) ---------- */
                    Path dir = Files.createTempDirectory(
                            privateSandbox(), "mantimetrics-git-"+repo+"-");
                    tmpDirs.add(dir);

                    LOG.info("Cloning https://github.com/{}/{}#{} → {}", owner, repo, branch, dir);

                    Git.cloneRepository()
                            .setURI("https://github.com/"+owner+"/"+repo+".git")
                            .setDirectory(dir.toFile())
                            .setBranch(branch)
                            .setCloneAllBranches(false)
                            .call()
                            .close();

                    /* ---------- JGit scan ---------- */
                    return Collections.unmodifiableMap(buildFileMap(dir, branch));

                } catch (Exception e) {
                    throw new UncheckedIOException(new IOException(e));
                }
            });
        } catch (UncheckedIOException uio) {
            throw new FileKeyMappingException(
                    "Failed to build file→issue map for "+cacheKey+"@"+branch,
                    uio.getCause());
        }
    }

    /* helper that performs the RevWalk / Diff scan; identical to your previous code */
    private Map<String,List<String>> buildFileMap(Path repoDir, String branch) throws Exception {

        Map<String,List<String>> fileMap = new HashMap<>();

        try (Repository repo = new FileRepositoryBuilder()
                .setGitDir(repoDir.resolve(".git").toFile())
                .build();
             RevWalk walk     = new RevWalk(repo);
             DiffFormatter df = new DiffFormatter(DisabledOutputStream.INSTANCE)) {

            df.setRepository(repo);
            ObjectId head = repo.resolve(branch);
            walk.markStart(walk.parseCommit(head));

            for (RevCommit c : walk) {
                List<String> keys = extractKeys(c.getFullMessage());
                if (keys.isEmpty()) continue;

                RevCommit parent = c.getParentCount() > 0 ? walk.parseCommit(c.getParent(0)) : null;
                for (DiffEntry d : df.scan(parent, c)) {
                    String p = d.getNewPath();
                    if (p.endsWith(".java")) {
                        fileMap.computeIfAbsent(p, __ -> new ArrayList<>()).addAll(keys);
                    }
                }
            }
        }
        LOG.info("Built file→JIRA map: {} java files", fileMap.size());
        return fileMap;
    }

    /* ====================================================================== */
    /* small utilities                                                        */
    /* ====================================================================== */
    private static List<String> extractKeys(String msg) {
        List<String> list = new ArrayList<>();
        Matcher m = JIRA_RE.matcher(msg);
        while (m.find()) list.add(m.group(1));
        return list;
    }

    private JsonNode getWithRetry(String url) throws IOException,InterruptedException {
        for (int a = 0; a < MAX_RETRIES; a++) {
            Request r = new Request.Builder()
                    .url(url)
                    .header("Authorization", "token " + token)
                    .header("Accept", "application/vnd.github.v3+json")
                    .build();
            try (Response resp = client.newCall(r).execute()) {
                if (resp.isSuccessful() && resp.body() != null)
                    return json.readTree(resp.body().string());

                if (resp.code() == 403 || resp.code() == 429) {
                    long wait = computeBackoff(resp, a);
                    LOG.warn("Rate-limit {}, retry {}/{} in {} s – {}",
                            resp.code(), a+1, MAX_RETRIES, wait/1_000, url);
                    TimeUnit.MILLISECONDS.sleep(wait);
                    continue;
                }
                throw new IOException("GitHub HTTP " + resp.code() + " – " + url);
            }
        }
        throw new IOException("Retries exhausted for " + url);
    }

    private static long computeBackoff(Response resp, int attempt) {
        String reset = resp.header("X-RateLimit-Reset");
        if (reset != null)
            return Math.max(Long.parseLong(reset)*1_000 - System.currentTimeMillis(), 5_000);
        return (long) (3_000 * Math.pow(2, attempt));
    }

    /** PRIVATE sandbox – now 100 % portable. */
    private static Path privateSandbox() throws IOException {
        Path home = Paths.get(System.getProperty("user.home"));
        Path box  = home.resolve(PRIV_DIR_NAME);

        if (Files.notExists(box)) {
            /* POSIX permissions only when the FS supports them – avoids the Windows crash */
            if (FileSystems.getDefault()
                    .supportedFileAttributeViews()
                    .contains("posix")) {
                Files.createDirectory(
                        box,
                        PosixFilePermissions.asFileAttribute(
                                PosixFilePermissions.fromString("rwx------")));
            } else {
                Files.createDirectory(box);
            }
        }
        return box;
    }

    public List<Path> getTmpDirs() { return List.copyOf(tmpDirs); }
}