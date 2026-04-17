package com.mantimetrics.git;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests for {@link ProjectConfig}.
 */
class ProjectConfigTest {

    /**
     * Verifies that the default analyzed-release percentage is 100 when omitted.
     */
    @Test
    void defaultsPercentageToHundredWhenMissing() {
        ProjectConfig cfg = new ProjectConfig("apache", "Avro", null, "AVRO");

        assertEquals(100, cfg.percentage());
    }

    /**
     * Verifies that an explicit release percentage is preserved.
     */
    @Test
    void keepsExplicitPercentage() {
        ProjectConfig cfg = new ProjectConfig("apache", "BookKeeper", 33, "BOOKKEEPER");

        assertEquals(33, cfg.percentage());
    }

    /**
     * Verifies that owner and repository name can be derived from the repository URL.
     */
    @Test
    void derivesOwnerAndNameFromRepositoryUrlWhenNeeded() {
        ProjectConfig cfg = new ProjectConfig(null, null,
                "https://github.com/apache/avro.git", 33, "AVRO");

        assertEquals("apache", cfg.owner());
        assertEquals("avro", cfg.name());
    }
}
