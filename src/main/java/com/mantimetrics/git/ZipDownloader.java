package com.mantimetrics.git;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.SocketTimeoutException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Semaphore;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

class ZipDownloader {
    private static final Logger LOG      = LoggerFactory.getLogger(ZipDownloader.class);
    private static final String ZIP      = "https://codeload.github.com";
    private static final String DIR_SUFF = ".mantimetrics-tmp";
    private final OkHttpClient  longClient;
    private final List<Path>    tmpDirs  = new CopyOnWriteArrayList<>();
    private static final int    MAX_R    = 5;
    private final Semaphore     permits  = new Semaphore(5_000, true);

    ZipDownloader(GitApiClient client) {
        this.longClient = client.http.newBuilder()
                .readTimeout(Duration.ofMinutes(10))
                .writeTimeout(Duration.ZERO)
                .build();
    }

    /** download + unzip con timeout esteso e retry su SocketTimeout. */
    Path downloadAndUnzip(String owner, String repo, String ref, String subDir)
            throws IOException, InterruptedException {
        String url = ZIP + "/" + owner + "/" + repo + "/zip/" +
                URLEncoder.encode(ref, StandardCharsets.UTF_8);
        IOException last = new SocketTimeoutException("Timeout downloading " + ref);
        for (int i = 0; i < MAX_R; i++) {
            try {
                return tryDownload(url, subDir);
            } catch (SocketTimeoutException ste) {
                last = ste;
                long wait = backoff(i);
                LOG.warn("Timeout downloading {}, retry {}/{} in {} s",
                        ref, i + 1, MAX_R, wait/1_000);
                Thread.sleep(wait);
            }
        }
        throw last;
    }

    /** Exponential backoff for rate-limit errors. */
    private static long backoff(int attempt){
        return (long)(3_000*Math.pow(2, attempt));
    }

    /**
     * It securely downloads a ZIP archive and expands it under ~/.mantimetrics-tmp.
     * – Previous Zip-Slip, symlink, decompression-bomb, too many entries ...
     */
    private Path tryDownload(String url, String subDir) throws IOException {

        permits.acquireUninterruptibly();

        Request req = new Request.Builder().url(url).build();
        try (Response resp = longClient.newCall(req).execute()) {
            if (!resp.isSuccessful() || resp.body() == null)
                throw new IOException("ZIP HTTP " + resp.code());

            /* root sandbox isolata */
            Path root = Files.createTempDirectory(getPrivateBox(),
                    "mantimetrics-" + subDir + '-');
            tmpDirs.add(root);

            long total = 0;
            int entries = 0;

            try (InputStream in = resp.body().byteStream();
                 ZipInputStream zis = new ZipInputStream(in)) {

                ZipEntry ze;
                while ((ze = ZipExtractionUtils.safeNextEntry(zis)) != null) {

                    ZipExtractionUtils.validateEntry(++entries);
                    Path target = ZipExtractionUtils.safeTarget(root, ze.getName());

                    if (ze.isDirectory()) {
                        Files.createDirectories(target);
                    } else {
                        long written = ZipExtractionUtils.extractFile(zis, target);
                        total = ZipExtractionUtils.checkQuotas(total, written, ze.getCompressedSize());
                    }

                    zis.closeEntry();
                }
            }
            return root;
        }
    }

    /** Deletes the temporary directory and all its contents. */
    private static Path getPrivateBox() throws IOException {
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
    public List<Path> getTmpDirs() { return List.copyOf(tmpDirs); }
}