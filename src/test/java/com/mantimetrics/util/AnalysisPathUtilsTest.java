package com.mantimetrics.util;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link AnalysisPathUtils}.
 */
class AnalysisPathUtilsTest {

    /**
     * Verifies that dataset paths are normalized by trimming separators.
     */
    @Test
    void normalizesDatasetPathsStoredWithCsvDelimiters() {
        assertEquals("src/main/java/com/example/Foo.java",
                AnalysisPathUtils.normalizeDatasetPath("/src/main/java/com/example/Foo.java/"));
    }

    /**
     * Verifies that the archive root folder is stripped when building relative source paths.
     */
    @Test
    void stripsArchiveRootWhenBuildingRelativeSourcePath() {
        Path root = Path.of("C:/tmp/release").toAbsolutePath();
        Path file = root.resolve("avro-1.0.0/src/main/java/com/example/Foo.java");

        assertEquals("src/main/java/com/example/Foo.java",
                AnalysisPathUtils.toRelativeSourcePath(root, file));
    }

    /**
     * Verifies that paths outside the extracted release root are rejected.
     */
    @Test
    void rejectsPathsOutsideTheExtractedReleaseRoot() {
        Path root = Path.of("C:/tmp/release").toAbsolutePath();

        assertFalse(AnalysisPathUtils.toRelativeSourcePath(root, "C:/other/place/Foo.java").isPresent());
        assertTrue(AnalysisPathUtils.toRelativeSourcePath(root, root.resolve("repo/src/Foo.java").toString()).isPresent());
    }
}
