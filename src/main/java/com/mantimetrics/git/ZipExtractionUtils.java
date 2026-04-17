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

/**
 * Defensive helpers for safely reading ZIP archives obtained from GitHub.
 */
class ZipExtractionUtils {
    private static final Logger LOG                 = LoggerFactory.getLogger(ZipExtractionUtils.class);
    private static final int    MAX_ENTRIES         = 20_000;
    private static final long   MAX_TOTAL_BYTES     = 2L  * 1024 * 1024 * 1024;
    private static final double MAX_INFLATION_RATIO = 200.0;

    /**
     * Prevents instantiation of the static utility class.
     */
    private ZipExtractionUtils() {}

    /**
     * Returns the next "safe" entry from the {@link ZipInputStream},
     * or {@code null} if there are no others.
     *
     * <p>Filters entries with dangerous name (Zip-Slip, absolute path,
     * NUL characters, etc.) and with negative compressed size,
     * continuing to search until it finds a valid entry.</p>
     *
     * @param zis ZIP stream to read from
     * @return next safe entry, or {@code null} when the archive is exhausted
     * @throws IOException when the ZIP stream cannot be read or contains invalid metadata
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

    /**
     * Validates an entry name against common ZIP traversal and path spoofing attacks.
     *
     * @param name entry name to validate
     * @return {@code true} when the entry name is unsafe
     */
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

    /**
     * Validates the number of processed ZIP entries against the configured quota.
     *
     * @param count number of processed entries
     * @throws IOException when the quota is exceeded
     */
    static void validateEntry(int count) throws IOException {
        if (count > MAX_ENTRIES) throw new IOException("Too many entries: " + count);
    }

    /**
     * Resolves an extraction target and verifies that it stays inside the root directory.
     *
     * @param root extraction root directory
     * @param name entry name to resolve
     * @return normalized extraction target path
     * @throws IOException when the entry attempts path traversal
     */
    @SuppressWarnings("unused")
    static Path safeTarget(Path root, String name) throws IOException {
        /* normalized path + traversal control */
        Path out = root.resolve(name).normalize();
        if (!out.startsWith(root)) throw new IOException("Traversal attempt: " + name);
        /* reject symbolic links: prevents 'zip slip' via symlinks */
        if (name.contains("..") || name.contains(":") || name.contains("\0"))
            throw new IOException("Nome archivio sospetto: " + name);
        return out;
    }

    /**
     * Extracts the current ZIP entry to a filesystem path.
     *
     * @param zis ZIP stream positioned at the desired entry
     * @param target output path for the extracted file
     * @return number of written bytes
     * @throws IOException when extraction fails
     */
    @SuppressWarnings("unused")
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

    /**
     * Updates the cumulative uncompressed size and validates archive inflation quotas.
     *
     * @param tot bytes extracted so far
     * @param add bytes extracted for the current entry
     * @param comp compressed size reported for the current entry
     * @return updated cumulative extracted size
     * @throws IOException when size or inflation quotas are exceeded
     */
    static long checkQuotas(long tot, long add, long comp) throws IOException {
        long newTot = tot + add;
        if (newTot > MAX_TOTAL_BYTES) throw new IOException("Uncompressed too large");
        if (comp > 0 && (double)add/comp > MAX_INFLATION_RATIO)
            throw new IOException("Inflation ratio > " + MAX_INFLATION_RATIO);
        return newTot;
    }

    /**
     * Reports whether a ZIP entry should be kept as a production Java source file.
     *
     * @param name ZIP entry name
     * @param directory whether the entry is a directory
     * @return {@code true} when the entry is a production Java source file
     */
    static boolean shouldMaterialize(String name, boolean directory) {
        if (directory) {
            return false;
        }
        return name.endsWith(".java")
                && !name.matches("(?i).*/src/test/java/.*")
                && !name.matches("(?i).*/test/.*")
                && !name.matches(".*(Test|IT)\\.java$");
    }
}
