package com.mantimetrics;

import com.mantimetrics.git.ProjectConfig;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectSelectionPromptTest {

    @Test
    void selectsConfiguredProjectByIndex() throws IOException {
        ProjectConfig[] configs = new ProjectConfig[] {
                new ProjectConfig("apache", "bookkeeper", "https://github.com/apache/bookkeeper.git", 33, "BOOKKEEPER"),
                new ProjectConfig("apache", "avro", "https://github.com/apache/avro.git", 33, "AVRO")
        };
        ProjectSelectionPrompt prompt = newPrompt("2\n");

        ProjectConfig selected = prompt.prompt(configs);

        assertEquals("avro", selected.name());
        assertEquals("AVRO", selected.jiraProjectKey());
    }

    @Test
    void supportsCustomRepositoryEntry() throws IOException {
        ProjectSelectionPrompt prompt = newPrompt(String.join("\n",
                "https://github.com/apache/cassandra.git",
                "CASSANDRA",
                ""
        ) + "\n");

        ProjectConfig selected = prompt.prompt(new ProjectConfig[0]);

        assertEquals("apache", selected.owner());
        assertEquals("cassandra", selected.name());
        assertEquals("CASSANDRA", selected.jiraProjectKey());
        assertEquals(33, selected.percentage());
    }

    @Test
    void retriesAfterInvalidMenuChoice() throws IOException {
        ProjectConfig[] configs = new ProjectConfig[] {
                new ProjectConfig("apache", "bookkeeper", "https://github.com/apache/bookkeeper.git", 33, "BOOKKEEPER")
        };
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ProjectSelectionPrompt prompt = new ProjectSelectionPrompt(
                new ByteArrayInputStream("9\n1\n".getBytes(StandardCharsets.UTF_8)),
                new PrintStream(output, true, StandardCharsets.UTF_8)
        );

        ProjectConfig selected = prompt.prompt(configs);

        assertEquals("bookkeeper", selected.name());
        assertTrue(output.toString(StandardCharsets.UTF_8).contains("Scelta non valida"));
    }

    private ProjectSelectionPrompt newPrompt(String input) {
        return new ProjectSelectionPrompt(
                new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)),
                new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8)
        );
    }
}
