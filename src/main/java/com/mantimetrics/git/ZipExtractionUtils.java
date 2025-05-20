package com.mantimetrics.git;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

class ZipExtractionUtils {
    private static final Logger LOG                 = LoggerFactory.getLogger(ZipExtractionUtils.class);
    private static final int    MAX_ENTRIES         = 20_000;
    private static final long   MAX_TOTAL_BYTES     = 2L  * 1024 * 1024 * 1024;
    private static final double MAX_INFLATION_RATIO = 200.0;

    private ZipExtractionUtils() {}

    /**
     * Returns the next "safe" entry from the {@link ZipInputStream},
     * or {@code null} if there are no others.
     *
     * <p>Filters entries with dangerous name (Zip-Slip, absolute path,
     * NUL characters, etc.) and with negative compressed size,
     * continuing to search until it finds a valid entry.</p>.
     */
    static ZipEntry safeNextEntry(ZipInputStream zis) throws IOException {
        ZipEntry ze;

        // true loop: it iterates until it finds a valid entry or finishes the file
        while ((ze = zis.getNextEntry()) != null) {

            String name = ze.getName();

            // filters out suspicious names or negative compressed size
            if (nameValidation(name)) {
                LOG.warn("Skipping invalid entry name: {}", name);
                zis.closeEntry();
                continue;
            }
            if (ze.getCompressedSize() < 0) {
                zis.closeEntry();
                throw new IOException("ZIP: compressedSize negative for " + name);
            }

            return ze;
        }
        return null;
    }

    private static boolean nameValidation(String name) {
        if (name.isBlank()) {
            LOG.warn("Skipping entry: blank name");
            return true;
        }
        if (name.length() > 4096) {
            LOG.warn("Skipping entry: name too long ({})", name.length());
            return true;
        }
        if (name.startsWith("/")) {
            LOG.warn("Skipping entry: starts with '/'");
            return true;
        }
        if (name.startsWith("\\")) {
            LOG.warn("Skipping entry: starts with '\\'");
            return true;
        }
        if (name.contains("..")) {
            LOG.warn("Skipping entry: contains '..'");
            return true;
        }
        if (name.indexOf('\0') >= 0) {
            LOG.warn("Skipping entry: contains NUL character");
            return true;
        }
        // Windows path with a drive letter (e.g. 'C:' or 'D:\')
        if (name.length() > 1 && Character.isLetter(name.charAt(0)) && name.charAt(1) == ':') {
            LOG.warn("Skipping entry: Windows drive letter");
            return true;
        }
        return false;
    }

    /** Validates the number of entries in the ZIP file. */
    static void validateEntry(int count) throws IOException {
        if (count > MAX_ENTRIES) throw new IOException("Too many entries: " + count);
    }

    /** Verifies that the path is within the root directory. */
    static Path safeTarget(Path root, String name) throws IOException {
        /* normalized path + traversal control */
        Path out = root.resolve(name).normalize();
        if (!out.startsWith(root)) throw new IOException("Traversal attempt: " + name);
        /* reject symbolic links: prevents 'zip slip' via symlinks */
        if (name.contains("..") || name.contains(":") || name.contains("\0"))
            throw new IOException("Nome archivio sospetto: " + name);
        return out;
    }

    /** Extracts the ZIP entry to the given path. */
    static long extractFile(ZipInputStream zis, Path target) throws IOException {
        // creates parent directories if they are missing
        Files.createDirectories(target.getParent());

        long written = 0;
        byte[] buf = new byte[8 * 1024];
        int len;

        try (OutputStream os = Files.newOutputStream(target,
                StandardOpenOption.CREATE_NEW)) {
            while ((len = zis.read(buf)) > 0) {
                os.write(buf, 0, len);
                written += len;
            }
        }
        return written;
    }

    /** Updates the total size and checks the inflation ratio. */
    static long checkQuotas(long tot, long add, long comp) throws IOException {
        long newTot = tot + add;
        if (newTot > MAX_TOTAL_BYTES) throw new IOException("Uncompressed too large");
        if (comp > 0 && (double)add/comp > MAX_INFLATION_RATIO)
            throw new IOException("Inflation ratio > " + MAX_INFLATION_RATIO);
        return newTot;
    }
}