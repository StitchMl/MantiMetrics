package com.mantimetrics.util;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnalysisPathUtilsTest {

    @Test
    void normalizesDatasetPathsStoredWithCsvDelimiters() {
        assertEquals("src/main/java/com/example/Foo.java",
                AnalysisPathUtils.normalizeDatasetPath("/src/main/java/com/example/Foo.java/"));
    }

    @Test
    void stripsArchiveRootWhenBuildingRelativeSourcePath() {
        Path root = Path.of("C:/tmp/release").toAbsolutePath();
        Path file = root.resolve("avro-1.0.0/src/main/java/com/example/Foo.java");

        assertEquals("src/main/java/com/example/Foo.java",
                AnalysisPathUtils.toRelativeSourcePath(root, file));
    }

    @Test
    void rejectsPathsOutsideTheExtractedReleaseRoot() {
        Path root = Path.of("C:/tmp/release").toAbsolutePath();

        assertFalse(AnalysisPathUtils.toRelativeSourcePath(root, "C:/other/place/Foo.java").isPresent());
        assertTrue(AnalysisPathUtils.toRelativeSourcePath(root, root.resolve("repo/src/Foo.java").toString()).isPresent());
    }
}
