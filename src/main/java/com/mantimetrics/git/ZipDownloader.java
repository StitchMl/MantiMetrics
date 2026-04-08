package com.mantimetrics.git;

import com.mantimetrics.parser.ParsedSourceFile;
import com.mantimetrics.parser.SourceScanResult;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.SocketTimeoutException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

class ZipDownloader {
    private static final Logger LOG = LoggerFactory.getLogger(ZipDownloader.class);
    private static final String ZIP = "https://codeload.github.com";
    private static final int MAX_R = 5;

    private final OkHttpClient longClient;
    private final Semaphore permits = new Semaphore(5_000, true);

    ZipDownloader(GitApiClient client) {
        this.longClient = client.http.newBuilder()
                .readTimeout(Duration.ofMinutes(10))
                .writeTimeout(Duration.ZERO)
                .build();
    }

    SourceScanResult downloadSources(String owner, String repo, String ref)
            throws IOException, InterruptedException {
        String url = ZIP + "/" + owner + "/" + repo + "/zip/" +
                URLEncoder.encode(ref, StandardCharsets.UTF_8);
        IOException last = new SocketTimeoutException("Timeout downloading " + ref);
        for (int i = 0; i < MAX_R; i++) {
            try {
                return tryDownload(url, owner + "/" + repo + "@" + ref);
            } catch (SocketTimeoutException exception) {
                last = exception;
                long wait = backoff(i);
                LOG.warn("Timeout downloading {}, retry {}/{} in {} s",
                        ref, i + 1, MAX_R, wait / 1_000);
                Thread.sleep(wait);
            }
        }
        throw last;
    }

    private static long backoff(int attempt) {
        return (long) (3_000 * Math.pow(2, attempt));
    }

    private SourceScanResult tryDownload(String url, String releaseId) throws IOException {
        permits.acquireUninterruptibly();
        try {
            Request request = new Request.Builder().url(url).build();
            try (Response response = longClient.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    throw new IOException("ZIP HTTP " + response.code());
                }

                List<ParsedSourceFile> sources = new ArrayList<>();
                long total = 0;
                int entries = 0;
                try (InputStream inputStream = response.body().byteStream();
                     ZipInputStream zipInputStream = new ZipInputStream(inputStream)) {
                    ZipEntry entry;
                    while ((entry = ZipExtractionUtils.safeNextEntry(zipInputStream)) != null) {
                        entries++;
                        ZipExtractionUtils.validateEntry(entries);
                        if (!ZipExtractionUtils.shouldMaterialize(entry.getName(), entry.isDirectory())) {
                            zipInputStream.closeEntry();
                            continue;
                        }

                        byte[] bytes = readEntryBytes(zipInputStream);
                        total = ZipExtractionUtils.checkQuotas(total, bytes.length, entry.getCompressedSize());
                        sources.add(new ParsedSourceFile(
                                toRelativeSourcePath(entry.getName()),
                                new String(bytes, StandardCharsets.UTF_8),
                                List.of()));
                        zipInputStream.closeEntry();
                    }
                }
                return new SourceScanResult(releaseId, sources.size(), List.copyOf(sources));
            }
        } finally {
            permits.release();
        }
    }

    private byte[] readEntryBytes(ZipInputStream zipInputStream) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[8 * 1024];
        int length;
        while ((length = zipInputStream.read(buffer)) > 0) {
            outputStream.write(buffer, 0, length);
        }
        return outputStream.toByteArray();
    }

    private String toRelativeSourcePath(String entryName) {
        String normalized = entryName.replace('\\', '/');
        int firstSlash = normalized.indexOf('/');
        return firstSlash >= 0 ? normalized.substring(firstSlash + 1) : normalized;
    }

    public List<Path> getTmpDirs() {
        return List.of();
    }
}
