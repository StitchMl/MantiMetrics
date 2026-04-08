package com.mantimetrics.git;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProjectConfigTest {

    @Test
    void defaultsPercentageToHundredWhenMissing() {
        ProjectConfig cfg = new ProjectConfig("apache", "Avro", null, "AVRO");

        assertEquals(100, cfg.percentage());
    }

    @Test
    void keepsExplicitPercentage() {
        ProjectConfig cfg = new ProjectConfig("apache", "BookKeeper", 33, "BOOKKEEPER");

        assertEquals(33, cfg.percentage());
    }

    @Test
    void derivesOwnerAndNameFromRepositoryUrlWhenNeeded() {
        ProjectConfig cfg = new ProjectConfig(null, null,
                "https://github.com/apache/avro.git", 33, "AVRO");

        assertEquals("apache", cfg.owner());
        assertEquals("avro", cfg.name());
    }
}
