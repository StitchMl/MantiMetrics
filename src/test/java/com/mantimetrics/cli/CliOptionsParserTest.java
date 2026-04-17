package com.mantimetrics.cli;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link CliOptionsParser}.
 */
class CliOptionsParserTest {
    private final CliOptionsParser parser = new CliOptionsParser();

    /**
     * Verifies that class granularity is used when the CLI omits the option.
     */
    @Test
    void defaultsGranularityToClassWhenMissing() {
        CliOptions options = parser.parse(new String[0]);

        assertEquals(GranularityOption.CLASS, options.granularityOption());
        assertFalse(options.hasCliProject());
    }

    /**
     * Verifies that repository-specific CLI arguments produce a single explicit project configuration.
     */
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

    /**
     * Verifies that a repository URL without a Jira key is rejected.
     */
    @Test
    void rejectsRepoUrlWithoutJiraKey() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> parser.parse(new String[] { "--repo-url=https://github.com/apache/avro.git" }));

        assertTrue(exception.getMessage().contains("--jira-key"));
    }

    /**
     * Verifies that project-specific options cannot be used without a repository URL.
     */
    @Test
    void rejectsProjectSpecificOptionsWithoutRepoUrl() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> parser.parse(new String[] { "--percentage=33" }));

        assertTrue(exception.getMessage().contains("--repo-url"));
    }
}
