package com.mantimetrics.jira;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JiraConfigurationLoaderTest {

    @TempDir
    Path tempDir;

    @Test
    void loadsPatFromIgnoredLocalOverrideFile() throws Exception {
        Path overrideFile = tempDir.resolve("jira.local.properties");
        Files.writeString(overrideFile, "jira.pat=test-jira-token" + System.lineSeparator(), StandardCharsets.UTF_8);

        String previousOverridePath = System.getProperty("mantimetrics.jira.override.path");
        String previousDirectPat = System.getProperty("mantimetrics.jira.pat");
        try {
            System.setProperty("mantimetrics.jira.override.path", overrideFile.toString());
            System.clearProperty("mantimetrics.jira.pat");

            JiraProjectSession session = new JiraConfigurationLoader().load(JiraClient.class, "/jira.properties", "BOOK");

            assertEquals("Bearer test-jira-token", session.authHeader());
        } finally {
            restoreProperty("mantimetrics.jira.override.path", previousOverridePath);
            restoreProperty("mantimetrics.jira.pat", previousDirectPat);
        }
    }

    private void restoreProperty(String key, String value) {
        if (value == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, value);
        }
    }
}
