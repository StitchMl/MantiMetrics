package com.mantimetrics.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

public final class TempDirectoryCleaner {
    private static final Logger LOG = LoggerFactory.getLogger(TempDirectoryCleaner.class);

    private TempDirectoryCleaner() {
        throw new AssertionError("Do not instantiate TempDirectoryCleaner");
    }

    public static void cleanup(List<Path> directories) {
        for (Path directory : directories) {
            try (Stream<Path> paths = Files.walk(directory)) {
                paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (Exception exception) {
                        LOG.warn("Cannot delete {}: {}", path, exception.getMessage());
                    }
                });
            } catch (Exception exception) {
                LOG.warn("Cannot delete {}: {}", directory, exception.getMessage());
            }
        }
    }
}
