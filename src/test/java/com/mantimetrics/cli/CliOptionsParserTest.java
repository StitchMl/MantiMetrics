package com.mantimetrics.cli;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CliOptionsParserTest {
    private final CliOptionsParser parser = new CliOptionsParser();

    @Test
    void defaultsGranularityToClassWhenMissing() {
        CliOptions options = parser.parse(new String[0]);

        assertEquals(GranularityOption.CLASS, options.granularityOption());
        assertFalse(options.hasCliProject());
    }

    @Test
    void buildsSingleProjectFromRepoUrlAndJiraKey() {
        CliOptions options = parser.parse(new String[] {
                "--granularity=both",
                "--repo-url=https://github.com/apache/avro.git",
                "--jira-key=AVRO"
        });

        assertEquals(GranularityOption.BOTH, options.granularityOption());
        assertTrue(options.hasCliProject());
        assertEquals("apache", options.cliProject().owner());
        assertEquals("avro", options.cliProject().name());
        assertEquals(33, options.cliProject().percentage());
    }

    @Test
    void rejectsRepoUrlWithoutJiraKey() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> parser.parse(new String[] { "--repo-url=https://github.com/apache/avro.git" }));

        assertTrue(exception.getMessage().contains("--jira-key"));
    }

    @Test
    void rejectsProjectSpecificOptionsWithoutRepoUrl() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> parser.parse(new String[] { "--percentage=33" }));

        assertTrue(exception.getMessage().contains("--repo-url"));
    }
}
