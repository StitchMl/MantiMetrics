package com.mantimetrics.projectSelector;

import com.mantimetrics.git.GitConfig;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the interactive {@link ProjectSelection}.
 */
class ProjectSelectionPromptTest {

    /**
     * Verifies that the prompt returns the configured project selected by menu index.
     */
    @Test
    void selectsConfiguredProjectByIndex() throws IOException {
        GitConfig[] configs = new GitConfig[] {
                new GitConfig("apache", "bookkeeper", "https://github.com/apache/bookkeeper.git", 33, "BOOKKEEPER", null),
                new GitConfig("apache", "avro", "https://github.com/apache/avro.git", 33, "AVRO", null)
        };
        ProjectSelection prompt = newPrompt("2\n");

        GitConfig selected = prompt.prompt(configs);

        assertEquals("avro", selected.name());
        assertEquals("AVRO", selected.jiraProjectKey());
    }

    /**
     * Verifies that the prompt supports manual entry of a custom repository.
     */
    @Test
    void supportsCustomRepositoryEntry() throws IOException {
        ProjectSelection prompt = newPrompt(String.join("\n",
                "https://github.com/apache/cassandra.git",
                "CASSANDRA",
                ""
        ) + "\n");

        GitConfig selected = prompt.prompt(new GitConfig[0]);

        assertEquals("apache", selected.owner());
        assertEquals("cassandra", selected.name());
        assertEquals("CASSANDRA", selected.jiraProjectKey());
        assertEquals(33, selected.percentage());
    }

    /**
     * Verifies that the prompt retries after an invalid menu selection.
     */
    @Test
    void retriesAfterInvalidMenuChoice() throws IOException {
        GitConfig[] configs = new GitConfig[] {
                new GitConfig("apache", "bookkeeper", "https://github.com/apache/bookkeeper.git", 33, "BOOKKEEPER", null)
        };
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ProjectSelection prompt = new ProjectSelection(
                new ByteArrayInputStream("9\n1\n".getBytes(StandardCharsets.UTF_8)),
                new PrintStream(output, true, StandardCharsets.UTF_8)
        );

        GitConfig selected = prompt.prompt(configs);

        assertEquals("bookkeeper", selected.name());
        assertTrue(output.toString(StandardCharsets.UTF_8).contains("Scelta non valida"));
    }

    /**
     * Creates a prompt instance backed by in-memory streams.
     *
     * @param input simulated CLI input
     * @return prompt configured for tests
     */
    private ProjectSelection newPrompt(String input) {
        return new ProjectSelection(
                new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)),
                new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8)
        );
    }
}
