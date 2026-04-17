package com.mantimetrics.config;

import com.mantimetrics.MainApp;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests for {@link GitHubTokenLoader}.
 */
class GitHubTokenLoaderTest {

    @TempDir
    Path tempDir;

    /**
     * Verifies that the loader reads the token from the local override file when present.
     */
    @Test
    void loadsTokenFromIgnoredLocalOverrideFile() throws Exception {
        Path overrideFile = tempDir.resolve("github.local.properties");
        Files.writeString(overrideFile, "github.pat=test-token" + System.lineSeparator(), StandardCharsets.UTF_8);

        String previousOverridePath = System.getProperty("mantimetrics.github.override.path");
        String previousDirectToken = System.getProperty("mantimetrics.github.pat");
        try {
            System.setProperty("mantimetrics.github.override.path", overrideFile.toString());
            System.clearProperty("mantimetrics.github.pat");

            String token = new GitHubTokenLoader().load(MainApp.class);

            assertEquals("test-token", token);
        } finally {
            restoreProperty("mantimetrics.github.override.path", previousOverridePath);
            restoreProperty("mantimetrics.github.pat", previousDirectToken);
        }
    }

    /**
     * Restores a system property to its previous value after a test.
     *
     * @param key property name
     * @param value previous property value, or {@code null} to clear it
     */
    private void restoreProperty(String key, String value) {
        if (value == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, value);
        }
    }
}
