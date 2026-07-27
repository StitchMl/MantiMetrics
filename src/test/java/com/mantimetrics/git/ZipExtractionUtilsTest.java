package com.mantimetrics.git;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link GitZipExtractor}.
 */
class ZipExtractionUtilsTest {

    /**
     * Verifies that only production Java sources are kept when filtering ZIP entries.
     */
    @Test
    void materializesOnlyProductionJavaSources() {
        assertTrue(GitZipExtractor.shouldMaterialize("repo/src/main/java/com/acme/App.java", false));
        assertTrue(GitZipExtractor.shouldMaterialize("repo/module-info.java", false));

        assertFalse(GitZipExtractor.shouldMaterialize("repo/README.md", false));
        assertFalse(GitZipExtractor.shouldMaterialize("repo/src/test/java/com/acme/AppTest.java", false));
        assertFalse(GitZipExtractor.shouldMaterialize("repo/src/main/java/com/acme/AppTest.java", false));
        assertFalse(GitZipExtractor.shouldMaterialize("repo/docs/", true));
    }
}
