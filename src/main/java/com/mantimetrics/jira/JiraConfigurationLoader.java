package com.mantimetrics.jira;

import com.mantimetrics.config.PropertiesLoaderSupport;

import java.io.IOException;
import java.util.Properties;

/**
 * Loads Jira connection settings from bundled configuration, overrides and runtime properties.
 */
final class JiraConfigurationLoader {
    private final PropertiesLoaderSupport propertiesLoaderSupport = new PropertiesLoaderSupport();

    /**
     * Loads and resolves the Jira session for a project key.
     *
     * @param resourceOwner class used to resolve classpath resources
     * @param propsPath filesystem or classpath location of the base Jira properties
     * @param projectKey Jira project key
     * @return initialized Jira project session
     * @throws JiraClientException when the properties cannot be loaded or are invalid
     */
    JiraProjectSession load(Class<?> resourceOwner, String propsPath, String projectKey) throws JiraClientException {
        try {
            Properties properties = propertiesLoaderSupport.loadResourceOrFile(
                    resourceOwner,
                    propsPath,
                    "JIRA configuration file not found"
            );
            propertiesLoaderSupport.mergeOptionalFile(
                    properties,
                    System.getProperty("mantimetrics.jira.override.path", "config/jira.local.properties"));
            propertiesLoaderSupport.overrideWithSystemOrEnv(
                    properties, "jira.url", "mantimetrics.jira.url", "MANTIMETRICS_JIRA_URL");
            propertiesLoaderSupport.overrideWithSystemOrEnv(
                    properties, "jira.pat", "mantimetrics.jira.pat", "MANTIMETRICS_JIRA_PAT");
            propertiesLoaderSupport.overrideWithSystemOrEnv(
                    properties, "jira.query", "mantimetrics.jira.query", "MANTIMETRICS_JIRA_QUERY");
            return JiraProjectSession.fromProperties(properties, projectKey);
        } catch (IOException exception) {
            throw new JiraClientException("Error loading JIRA properties", exception);
        }
    }
}
