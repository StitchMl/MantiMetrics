package com.mantimetrics.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Deletes temporary directories created during source-download and analysis phases.
 */
public final class TempDirectoryCleaner {
    private static final Logger LOG = LoggerFactory.getLogger(TempDirectoryCleaner.class);

    /**
     * Prevents instantiation of the static utility class.
     */
    private TempDirectoryCleaner() {
        throw new AssertionError("Do not instantiate TempDirectoryCleaner");
    }

    /**
     * Recursively deletes each supplied directory and logs best-effort cleanup failures.
     *
     * @param directories temporary directories to delete
     */
    public static void cleanup(List<Path> directories) {
        for (Path directory : directories) {
            try (Stream<Path> paths = Files.walk(directory)) {
                paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (Exception exception) {
                        LOG.warn("[INT] Cannot delete {}: {}", path, exception.getMessage());
                    }
                });
            } catch (Exception exception) {
                LOG.warn("[EXT] Cannot delete {}: {}", directory, exception.getMessage());
            }
        }
    }
}
