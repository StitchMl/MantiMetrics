package com.mantimetrics.jira;

import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link JiraProjectSession}.
 */
class JiraProjectSessionTest {

    /**
     * Verifies that a valid property set produces a normalized Jira session.
     */
    @Test
    void buildsSessionFromPropertiesAndNormalizesSearchBase() throws Exception {
        Properties properties = new Properties();
        properties.setProperty("jira.url", "https://jira.example.com/");
        properties.setProperty("jira.pat", "secret");
        properties.setProperty("jira.query", "project = {projectKey} AND type = Bug");

        JiraProjectSession session = JiraProjectSession.fromProperties(properties, "BOOK");

        assertEquals("https://jira.example.com", session.baseUrl());
        assertEquals("Bearer secret", session.authHeader());
        assertTrue(session.searchBase().contains("project+%3D+BOOK"));
    }

    /**
     * Verifies that missing required Jira properties are rejected.
     */
    @Test
    void rejectsMissingRequiredProperties() {
        Properties properties = new Properties();
        properties.setProperty("jira.url", "https://jira.example.com");

        JiraClientException exception = assertThrows(
                JiraClientException.class,
                () -> JiraProjectSession.fromProperties(properties, "BOOK"));

        assertTrue(exception.getMessage().contains("jira.url, jira.pat and jira.query must be set"));
    }
}
