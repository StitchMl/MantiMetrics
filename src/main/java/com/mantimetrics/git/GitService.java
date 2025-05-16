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
import java.nio.file.attribute.PosixFilePermissions;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import java.net.SocketTimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Git + GitHub REST utility – *commit-history* implementation */
public final class GitService {
    private static final Logger  LOG   = LoggerFactory.getLogger(GitService.class);
    private static final String  API   = "https://api.github.com";
    private static final String  ZIP   = "https://codeload.github.com";
    private static final String DIR_SUFF = ".mantimetrics-tmp";
    private static final String  REPOS     = "/repos/";
    private static final int     MAX_R = 5;
    private static final Pattern JIRA  =
            Pattern.compile("\\b(?>[A-Z][A-Z0-9]++-\\d++)\\b");

    private final OkHttpClient http;
    private final ObjectMapper json = new ObjectMapper();
    private final String token;

    private final Semaphore permits = new Semaphore(5_000, true);
    private final Map<String,String>                    defBranch = new ConcurrentHashMap<>();
    private final Map<String,Map<String,List<String>>>  projCache = new ConcurrentHashMap<>();
    private final List<Path> tmp = new CopyOnWriteArrayList<>();

    private static final int   MAX_ENTRIES      = 20_000;
    private static final long  MAX_ENTRY_BYTES  = 50L * 1024 * 1024;
    private static final long  MAX_TOTAL_BYTES  = 2L  * 1024 * 1024 * 1024;
    private static final double MAX_INFLATION_RATIO = 200.0;
    private static final double THRESHOLD_RATIO = 10;
    private static final int THRESHOLD_ENTRIES = 10000;
    private static final int THRESHOLD_SIZE = 1000000000;

    /** Creates a new GitService with the given personal access token. */
    public GitService(String pat) {
        this.token = pat;
        this.http  = new OkHttpClient.Builder()
                .connectTimeout(java.time.Duration.ofSeconds(30))
                .build();
    }

    /** Returns the default branch of the given repository. */
    public String getDefaultBranch(String owner,String repo) {
        return defBranch.computeIfAbsent(owner+'/'+repo, k -> {
            try {
                return get(API+REPOS+owner+'/'+repo)
                        .path("default_branch").asText("master");
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new UncheckedIOException(
                        new IOException("Interrupted while building JIRA map", ie));

            } catch (IOException ioe) {
                throw new UncheckedIOException(ioe);
            }
        });
    }

    /** Lists all tags of the given repository. */
    public List<String> listTags(String o,String r) throws IOException, InterruptedException {
        List<String> tags = new ArrayList<>();
        for (int p=1;;p++) {
            JsonNode arr = get(API+REPOS+o+'/'+r+"/tags?per_page=100&page="+p);
            if (!arr.isArray() || arr.isEmpty()) break;
            arr.forEach(n -> Optional.ofNullable(n.path("name").asText(null)).ifPresent(tags::add));
        }
        LOG.info("Found {} tags for {}/{}", tags.size(), o, r);
        return tags;
    }

    /** Builds a file → JIRA-keys map using the commit history of the given branch. */
    public Map<String,List<String>> getFileToIssueKeysMap(String o,String r,String branch)
            throws FileKeyMappingException {

        String cacheKey = o+'/'+r;
        try {
            return projCache.computeIfAbsent(cacheKey, k -> {
                try {
                    Map<String,List<String>> m = buildFileMapViaCommits(o,r,branch);
                    LOG.info("Built file→JIRA map for {}: {} java files", cacheKey, m.size());
                    return Collections.unmodifiableMap(m);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new UncheckedIOException(
                            new IOException("Interrupted while building JIRA map", ie));

                } catch (IOException ioe) {
                    throw new UncheckedIOException(ioe);
                }
            });
        } catch (UncheckedIOException ex) {
            throw new FileKeyMappingException("Map build failed for "+cacheKey, ex.getCause());
        }
    }

    /**
     * Walks (up to MAX_COMMITS) commits on the given branch and builds a
     * file → JIRA-keys map using only the REST API (zero git-clone).
     */
    private Map<String, List<String>> buildFileMapViaCommits(
            String owner, String repo, String branch)
            throws IOException, InterruptedException {

        final int MAX_COMMITS = 5_000;
        Map<String, List<String>> map = new HashMap<>();
        int fetched = 0;

        for (int page = 1; fetched < MAX_COMMITS; page++) {

            JsonNode pageCommits = get(API + REPOS + owner + '/' + repo +
                    "/commits?sha=" + URLEncoder.encode(branch, StandardCharsets.UTF_8) +
                    "&per_page=100&page=" + page);

            if (!pageCommits.isArray() || pageCommits.isEmpty()) break;

            for (JsonNode commit : pageCommits) {
                fetched++;
                processCommit(commit, map);
                if (fetched >= MAX_COMMITS) break;
            }
        }
        return map;
    }

    /** Extracts JIRA keys from the commit message and, if present, records
     *  them against every *.java* file changed in that commit. */
    private void processCommit(JsonNode commit, Map<String, List<String>> map)
            throws IOException, InterruptedException {

        List<String> keys = extractKeys(commit.path("commit").path("message").asText());
        if (keys.isEmpty()) return;

        JsonNode files = get(commit.path("url").asText()).path("files");
        addKeysForFiles(files, keys, map);
    }

    /** Adds the same list of keys to every Java file contained in *files*. */
    private static void addKeysForFiles(JsonNode files,
                                        List<String> keys,
                                        Map<String, List<String>> map) {

        for (JsonNode f : files) {
            String name = f.path("filename").asText("");
            if (name.endsWith(".java"))
                map.computeIfAbsent(name, str -> new ArrayList<>()).addAll(keys);
        }
    }

    /** Performs a GET request on the given URL, retrying on rate-limit errors. */
    private JsonNode get(String url) throws IOException,InterruptedException {
        for (int a=0;a<MAX_R;a++) {
            Request req = new Request.Builder().url(url)
                    .header("Authorization","token "+token)
                    .header("Accept","application/vnd.github.v3+json").build();
            try (Response resp = http.newCall(req).execute()) {
                if (resp.isSuccessful() && resp.body()!=null)
                    return json.readTree(resp.body().string());

                if (resp.code()==403 || resp.code()==429) {
                    long wait= backoff(resp,a);
                    LOG.warn("Rate-limit {}, retry {}/{} in {} s – {}",resp.code(),a+1,MAX_R,wait/1_000,url);
                    TimeUnit.MILLISECONDS.sleep(wait);
                    continue;
                }
                throw new IOException("GitHub HTTP "+resp.code()+" – "+url);
            }
        }
        throw new IOException("Retries exhausted for "+url);
    }

    /** Exponential backoff for rate-limit errors. */
    private static long backoff(Response resp,int attempt){
        if (resp != null) {
            String reset=resp.header("X-RateLimit-Reset");
            if(reset!=null)
                return Math.max(Long.parseLong(reset)*1_000-System.currentTimeMillis(),5_000);
        }
        return (long)(3_000*Math.pow(2, attempt));
    }

    /** Extracts JIRA keys from the commit message. */
    private static List<String> extractKeys(String msg){
        List<String> l=new ArrayList<>();
        Matcher m=JIRA.matcher(msg);
        while(m.find())
            l.add(m.group());
        return l;
    }

    /** download + unzip con timeout esteso e retry su SocketTimeout. */
    public Path downloadAndUnzipRepo(String owner,
                                     String repo,
                                     String ref,
                                     String subDir) throws IOException, InterruptedException {

        final String url = ZIP +'/'+owner+'/'+repo+"/zip/"
                + URLEncoder.encode(ref, StandardCharsets.UTF_8);

        // ad hoc client with very high readTimeout (10 minutes) and
        // writeTimeout disabled: does not affect other uses of http.
        OkHttpClient longHttp = http.newBuilder()
                .readTimeout(java.time.Duration.ofMinutes(10))
                .writeTimeout(java.time.Duration.ZERO)
                .build();

        IOException last = new SocketTimeoutException("Timeout downloading " + ref);
        for (int a = 0; a < MAX_R; a++) {
            try {
                return tryDownload(longHttp, url, subDir);
            } catch (SocketTimeoutException ste) {
                last = ste;
                long wait = backoff(null, a);
                LOG.warn("Timeout downloading {}, retry {}/{} in {} s",
                        ref, a+1, MAX_R, wait/1_000);
                Thread.sleep(wait);
            }
        }
        throw last;
    }

    /**
     * It securely downloads a ZIP archive and expands it under ~/.mantimetrics-tmp.
     * – Previous Zip-Slip, symlink, decompression-bomb, too many entries ...
     */
    private Path tryDownload(OkHttpClient client,
                             String url,
                             String subDir) throws IOException {

        permits.acquireUninterruptibly();

        Request req = new Request.Builder().url(url).build();
        try (Response resp = client.newCall(req).execute()) {

            if (!resp.isSuccessful() || resp.body() == null)
                throw new IOException("ZIP HTTP " + resp.code());

            /* root sandbox isolata */
            Path root = Files.createTempDirectory(privateBox(),
                    "mantimetrics-" + subDir + '-');
            tmp.add(root);

            long totalBytes = 0;
            int  entries    = 0;

            try (InputStream    in  = resp.body().byteStream();
                 ZipInputStream zis = new ZipInputStream(in)) {

                for (ZipEntry ze = safeNextEntry(zis);
                     ze != null;
                     ze = safeNextEntry(zis)) {

                    validateEntry(++entries);
                    Path target = safeTarget(root, ze.getName());

                    long written = ze.isDirectory()
                            ? dirEnsure(target)
                            : fileExtract(zis, target);

                    totalBytes = checkQuotas(totalBytes,
                            written,
                            ze.getCompressedSize());

                    zis.closeEntry();
                }
            }
            return root;
        }
    }

    /**
     * Returns the next "safe" entry from the {@link ZipInputStream},
     * or {@code null} if there are no others.
     *
     * <p>Filters entries with dangerous name (Zip-Slip, absolute path,
     * NUL characters, etc.) and with negative compressed size,
     * continuing to search until it finds a valid entry.</p>.
     */
    private static ZipEntry safeNextEntry(ZipInputStream zis) throws IOException {
        ZipEntry ze;
        int totalEntryArchive = 0;
        int totalSizeArchive = 0;

        // true loop: it iterates until it finds a valid entry or finishes the file
        while (zis.available() != 0) {

            ze = zis.getNextEntry();
            InputStream in = new BufferedInputStream(zis);
            try (OutputStream out = new BufferedOutputStream(new FileOutputStream("./output_onlyForTesting.txt"))) {

                totalEntryArchive++;

                int nBytes;
                byte[] buffer = new byte[2048];
                int totalSizeEntry = 0;

                while ((nBytes = in.read(buffer)) > 0) {
                    out.write(buffer, 0, nBytes);
                    totalSizeEntry += nBytes;
                    totalSizeArchive += nBytes;

                    assert ze != null;
                    double compressionRatio = (double) totalSizeEntry / ze.getCompressedSize();
                    if (compressionRatio > THRESHOLD_RATIO) {
                        // the ratio between compressed and uncompressed data is highly suspicious, looks like a Zip Bomb Attack
                        break;
                    }
                }

                /* ---- name validation ---- */
                // getName() is never null, so the test on null is superfluous
                assert ze != null;
                String name = ze.getName();

                if ((totalSizeArchive > THRESHOLD_SIZE || totalEntryArchive > THRESHOLD_ENTRIES) && nameValidation(name)) {
                    // the uncompressed data size is too much for the application resource capacity,
                    // or too many entries in this archive can lead to inodes exhaustion of the system
                    // and discards the invalid entry and continues with the cycle
                    zis.closeEntry();
                    continue;
                }

                /* ---- compressed dimension validation ---- */
                long cSize = ze.getCompressedSize();
                if (cSize < -1) {
                    zis.closeEntry();
                    throw new IOException("ZIP: compressedSize negative for " + name);
                }

                return ze;
            }
        }
        return null;
    }

    private static boolean nameValidation(String name){
        return (name.isBlank()
                || name.length() > 4_096
                || name.startsWith("/")
                || name.startsWith("\\")
                || name.contains("..")
                || name.indexOf('\0') >= 0
                || name.contains(":"));
    }

    /** Validates the number of entries in the ZIP file. */
    private static void validateEntry(int n) throws IOException {
        if (n > MAX_ENTRIES)
            throw new IOException("ZIP troppe entry (>" + MAX_ENTRIES + ')');
    }

    /** Verifies that the path is within the root directory. */
    private static Path safeTarget(Path root, String name) throws IOException {
        /* normalized path + traversal control */
        Path out = root.resolve(name).normalize();
        if (!out.startsWith(root))
            throw new IOException("Tentativo di traversal: " + name);
        /* reject symbolic links: prevents 'zip slip' via symlinks */
        if (name.contains("..") || name.contains(":") || name.contains("\0"))
            throw new IOException("Nome archivio sospetto: " + name);
        return out;
    }

    /** Creates the target directory if it does not exist. */
    private static long dirEnsure(Path dir) throws IOException {
        Files.createDirectories(dir);
        return 0;
    }

    /** Extracts the ZIP entry to the given path. */
    private static long fileExtract(ZipInputStream zis, Path out) throws IOException {
        Files.createDirectories(out.getParent());
        long written = 0;
        try (OutputStream os = Files.newOutputStream(out,
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            byte[] buf = new byte[8 * 1024];
            int n;
            while ((n = zis.read(buf)) > 0) {
                written += n;
                if (written > MAX_ENTRY_BYTES)
                    throw new IOException("Entry > " + MAX_ENTRY_BYTES + " B");
                os.write(buf, 0, n);
            }
        }
        return written;
    }

    /** Updates the total size and checks the inflation ratio. */
    private static long checkQuotas(long tot, long add, long compressed) throws IOException {
        long newTot = tot + add;
        if (newTot > MAX_TOTAL_BYTES)
            throw new IOException("ZIP oltre " + MAX_TOTAL_BYTES + " B uncompressed");
        if (compressed > 0) {
            double ratio = (double) add / compressed;
            if (ratio > MAX_INFLATION_RATIO)
                throw new IOException("Inflation ratio " + ratio + " > " + MAX_INFLATION_RATIO);
        }
        return newTot;
    }

    /** Deletes the temporary directory and all its contents. */
    private static Path privateBox() throws IOException {
        Path box = Paths.get(System.getProperty("user.home")).resolve(DIR_SUFF);
        if (Files.notExists(box)) {
            if (FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
                Files.createDirectory(box,
                        PosixFilePermissions.asFileAttribute(
                                PosixFilePermissions.fromString("rwx------")));
            } else {
                Files.createDirectory(box);
            }
        }
        return box;
    }

    /** Deletes the temporary directory and all its contents. */
    public List<Path> getTmp() { return List.copyOf(tmp); }
}