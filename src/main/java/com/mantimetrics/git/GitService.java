package com.mantimetrics.git;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Git + GitHub REST utility – *commit-history* implementation */
public final class GitService {

    /* ────────── costanti ────────── */
    private static final Logger  LOG   = LoggerFactory.getLogger(GitService.class);
    private static final String  API   = "https://api.github.com";
    private static final String  ZIP   = "https://codeload.github.com";
    private static final String DIR_SUFF = ".mantimetrics-tmp";
    private static final int     MAX_R = 5;
    private static final Pattern JIRA  =
            Pattern.compile("\\b(?>[A-Z][A-Z0-9]++-\\d++)\\b");

    /* ────────── status ────────── */
    private final OkHttpClient http;
    private final ObjectMapper json = new ObjectMapper();
    private final String token;

    private final Semaphore permits = new Semaphore(5_000, true);
    private final Map<String,String>                    defBranch = new ConcurrentHashMap<>();
    private final Map<String,Map<String,List<String>>>  projCache = new ConcurrentHashMap<>();
    private final List<Path> tmp = new CopyOnWriteArrayList<>();

    public GitService(String pat) {
        token = pat;
        http  = new OkHttpClient.Builder()
                .connectTimeout(java.time.Duration.ofSeconds(30))
                .build();
    }

    /* ═════════ GET DEFAULT BRANCH ═════════ */
    public String getDefaultBranch(String owner,String repo) {
        return defBranch.computeIfAbsent(owner+'/'+repo, k -> {
            try {
                return get(API+"/repos/"+owner+'/'+repo)
                        .path("default_branch").asText("master");
            } catch (Exception e) { throw new UncheckedIOException(new IOException(e)); }
        });
    }

    /* ═════════ LIST TAGS ═════════ */
    public List<String> listTags(String o,String r) throws Exception {
        List<String> tags = new ArrayList<>();
        for (int p=1;;p++) {
            JsonNode arr = get(API+"/repos/"+o+'/'+r+"/tags?per_page=100&page="+p);
            if (!arr.isArray() || arr.isEmpty()) break;
            arr.forEach(n -> Optional.ofNullable(n.path("name").asText(null)).ifPresent(tags::add));
        }
        LOG.info("Found {} tags for {}/{}", tags.size(), o, r);
        return tags;
    }

    /* ═════════ FILE → JIRA MAP (commit-history) ═════════ */
    public Map<String,List<String>> getFileToIssueKeysMap(String o,String r,String branch)
            throws FileKeyMappingException {

        String cacheKey = o+'/'+r;
        try {
            return projCache.computeIfAbsent(cacheKey, k -> {
                try {
                    Map<String,List<String>> m = buildFileMapViaCommits(o,r,branch);
                    LOG.info("Built file→JIRA map for {}: {} java files", cacheKey, m.size());
                    return Collections.unmodifiableMap(m);
                } catch (Exception e) { throw new UncheckedIOException(new IOException(e)); }
            });
        } catch (UncheckedIOException ex) {
            throw new FileKeyMappingException("Map build failed for "+cacheKey, ex.getCause());
        }
    }

    /**
     * Scroll through the entire branch history (100 × 100-page layout) - or
     * stops after <code>maxCommits</code> to contain the requests.
     */
    private Map<String,List<String>> buildFileMapViaCommits(
            String o,String r,String branch)
            throws IOException,InterruptedException {

        Map<String,List<String>> map = new HashMap<>();
        int fetched = 0;
        for (int page = 1; fetched < 5000; page++) {

            JsonNode commits = get(API+"/repos/"+o+'/'+r+"/commits?sha="+
                    URLEncoder.encode(branch, StandardCharsets.UTF_8)+
                    "&per_page=100&page="+page);

            if (!commits.isArray() || commits.isEmpty()) break;

            for (JsonNode c : commits) {
                fetched++;
                List<String> keys = extractKeys(c.path("commit").path("message").asText());
                if (keys.isEmpty()) continue;

                JsonNode files = get(c.path("url").asText()).path("files");
                for (JsonNode f : files) {
                    String name = f.path("filename").asText();
                    if (name.endsWith(".java"))
                        map.computeIfAbsent(name,k->new ArrayList<>()).addAll(keys);
                }
                if (fetched >= 5000) break;
            }
        }
        return map;
    }

    /* ═════════ Helpers HTTP & regex ═════════ */
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

    private static long backoff(Response r,int a){
        String reset=r.header("X-RateLimit-Reset");
        if(reset!=null) return Math.max(Long.parseLong(reset)*1_000-System.currentTimeMillis(),5_000);
        return (long)(3_000*Math.pow(2,a));
    }
    private static List<String> extractKeys(String msg){
        List<String> l=new ArrayList<>(); Matcher m=JIRA.matcher(msg); while(m.find()) l.add(m.group()); return l;
    }

    /* ═════════ ZIP download (unchanged, but remains 4-arg overload used by CodeParser) ═════════ */
    public Path downloadAndUnzipRepo(String o,String r,String ref,String subDir) throws IOException{
        String url = ZIP + '/'+o+'/'+r+"/zip/"+URLEncoder.encode(ref,StandardCharsets.UTF_8);
        permits.acquireUninterruptibly();
        Request req=new Request.Builder().url(url).build();

        try(Response resp=http.newCall(req).execute()){
            if(!resp.isSuccessful()||resp.body()==null) throw new IOException("ZIP HTTP "+resp.code());
            Path root=Files.createTempDirectory(privateBox(),"mantimetrics-"+subDir+'-'); tmp.add(root);

            try(InputStream in=resp.body().byteStream(); ZipInputStream zis=new ZipInputStream(in)){
                for(ZipEntry e; (e=zis.getNextEntry())!=null;){
                    Path out=root.resolve(e.getName()).normalize();
                    if(!out.startsWith(root)) throw new IOException("ZIP traversal: "+e.getName());
                    if(e.isDirectory()) Files.createDirectories(out);
                    else {Files.createDirectories(out.getParent()); Files.copy(zis,out,StandardCopyOption.REPLACE_EXISTING);}
                    zis.closeEntry();
                }
            }
            return root;
        }
    }

    private static Path privateBox() throws IOException{
        Path home=Paths.get(System.getProperty("user.home")); Path box=home.resolve(DIR_SUFF);
        if(Files.notExists(box)){
            if(FileSystems.getDefault().supportedFileAttributeViews().contains("posix")){
                Files.createDirectory(box, PosixFilePermissions.asFileAttribute(
                        PosixFilePermissions.fromString("rwx------")));
            }else Files.createDirectory(box);
        }
        return box;
    }
    public List<Path> getTmpDirs(){ return List.copyOf(tmp); }
}