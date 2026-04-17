package com.mantimetrics.jira;

import org.apache.http.client.utils.URIBuilder;

import java.util.Locale;
import java.util.Properties;

/**
 * Immutable Jira session data derived from configuration for one project.
 *
 * @param baseUrl normalized Jira base URL
 * @param authHeader bearer authorization header
 * @param searchBase base search URL including the configured JQL
 */
record JiraProjectSession(
        String baseUrl,
        String authHeader,
        String searchBase
) {
    /**
     * Builds a Jira session from the loaded properties and the selected project key.
     *
     * @param properties loaded Jira properties
     * @param projectKey Jira project key
     * @return initialized Jira project session
     * @throws JiraClientException when required properties are missing or the search URL cannot be built
     */
    static JiraProjectSession fromProperties(Properties properties, String projectKey) throws JiraClientException {
        String baseUrl = stripTrailingSlashes(properties.getProperty("jira.url", "").trim());
        String pat = properties.getProperty("jira.pat", "").trim();
        String queryTemplate = properties.getProperty("jira.query", "").trim();

        if (baseUrl.isEmpty() || pat.isEmpty() || queryTemplate.isEmpty()) {
            throw new JiraClientException("jira.url, jira.pat and jira.query must be set");
        }

        try {
            String searchBase = new URIBuilder(baseUrl + "/rest/api/2/search")
                    .addParameter("jql", queryTemplate.replace("{projectKey}", projectKey))
                    .build()
                    .toString();
            return new JiraProjectSession(baseUrl, "Bearer " + pat, searchBase);
        } catch (Exception exception) {
            throw new JiraClientException("JIRA URI construction error", exception);
        }
    }

    /**
     * Normalizes tags and Jira version names so Git and Jira releases can be compared reliably.
     *
     * @param value raw tag or version name
     * @return normalized identifier
     */
    static String normalize(String value) {
        return value.toLowerCase(Locale.ROOT)
                .replaceFirst("^(release-|rel-|ver-|v)", "")
                .trim();
    }

    /**
     * Removes trailing slashes from the configured Jira base URL.
     *
     * @param value raw base URL
     * @return URL without trailing slashes
     */
    private static String stripTrailingSlashes(String value) {
        int end = value.length();
        while (end > 0 && value.charAt(end - 1) == '/') {
            end--;
        }
        return value.substring(0, end);
    }
}
