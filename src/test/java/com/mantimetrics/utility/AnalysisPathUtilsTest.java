package com.mantimetrics.utility;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link PathUtility}.
 */
class AnalysisPathUtilsTest {

    /**
     * Verifies that dataset paths are normalized by trimming separators.
     */
    @Test
    void normalizesDatasetPathsStoredWithCsvDelimiters() {
        assertEquals("src/main/java/com/example/Foo.java",
                PathUtility.normalizeDatasetPath("/src/main/java/com/example/Foo.java/"));
    }

    /**
     * Verifies that the archive root folder is stripped when building relative source paths.
     */
    @Test
    void stripsArchiveRootWhenBuildingRelativeSourcePath() {
        Path root = Path.of("C:/tmp/release").toAbsolutePath();
        Path file = root.resolve("avro-1.0.0/src/main/java/com/example/Foo.java");

        assertEquals("src/main/java/com/example/Foo.java",
                PathUtility.toRelativeSourcePath(root, file));
    }

    /**
     * Verifies that paths outside the extracted release root are rejected.
     */
    @Test
    void rejectsPathsOutsideTheExtractedReleaseRoot() {
        Path root = Path.of("C:/tmp/release").toAbsolutePath();

        assertFalse(PathUtility.toRelativeSourcePath(root, "C:/other/place/Foo.java").isPresent());
        assertTrue(PathUtility.toRelativeSourcePath(root, root.resolve("repo/src/Foo.java").toString()).isPresent());
    }
}
