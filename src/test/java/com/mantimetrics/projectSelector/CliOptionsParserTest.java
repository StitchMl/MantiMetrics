package com.mantimetrics.projectSelector;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link CliParser}.
 */
class CliOptionsParserTest {
    private final CliParser parser = new CliParser();

    /**
     * Verifies that no CLI project is produced when the arguments are empty.
     */
    @Test
    void defaultsToNoCliProjectWhenMissing() {
        OptionsSelector options = parser.parse(new String[0]);
        assertFalse(options.hasCliProject());
    }

    /**
     * Verifies that repository-specific CLI arguments produce a single explicit project configuration.
     */
    @Test
    void buildsSingleProjectFromRepoUrlAndJiraKey() {
        OptionsSelector options = parser.parse(new String[] {
                "--repo-url=https://github.com/apache/avro.git",
                "--jira-key=AVRO"
        });

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
