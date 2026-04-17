package com.mantimetrics.util;

import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

/**
 * Utility methods for normalizing and relativizing analysis paths.
 */
public final class AnalysisPathUtils {

    /**
     * Prevents instantiation of the static utility class.
     */
    private AnalysisPathUtils() {
        throw new AssertionError("Do not instantiate AnalysisPathUtils");
    }

    /**
     * Normalizes a dataset path to a Unix-style relative representation without leading or trailing slashes.
     *
     * @param rawPath original path string
     * @return normalized dataset path
     */
    public static String normalizeDatasetPath(String rawPath) {
        String normalized = rawPath.replace('\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    /**
     * Converts an absolute or relative file path into a dataset-relative source path.
     * The first path segment below the source root is removed because it represents the extracted root folder.
     *
     * @param sourceRoot root directory containing the extracted source tree
     * @param filePath file to relativize
     * @return normalized relative source path with Unix separators
     * @throws IllegalArgumentException when the file is not contained in the source root
     */
    public static String toRelativeSourcePath(Path sourceRoot, Path filePath) {
        Path normalizedRoot = sourceRoot.toAbsolutePath().normalize();
        Path normalizedFile = filePath.toAbsolutePath().normalize();
        if (!normalizedFile.startsWith(normalizedRoot)) {
            throw new IllegalArgumentException(normalizedFile + " is not contained in " + normalizedRoot);
        }

        String relUnix = normalizedRoot.relativize(normalizedFile)
                .toString()
                .replace('\\', '/');

        int firstSlash = relUnix.indexOf('/');
        return firstSlash >= 0 ? relUnix.substring(firstSlash + 1) : relUnix;
    }

    /**
     * Safely converts a nullable file path string into a relative source path.
     *
     * @param sourceRoot root directory containing the extracted source tree
     * @param filePath nullable raw file path string
     * @return relative path wrapped in an {@link Optional}, or an empty optional when the input is blank or invalid
     */
    public static Optional<String> toRelativeSourcePath(Path sourceRoot, @Nullable String filePath) {
        if (filePath == null || filePath.isBlank()) {
            return Optional.empty();
        }

        try {
            Path path = Paths.get(filePath);
            return Optional.of(toRelativeSourcePath(sourceRoot, path));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }
}
