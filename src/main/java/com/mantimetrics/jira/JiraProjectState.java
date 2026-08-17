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
 * @param allTicketsSearchBase base search URL with the issuetype=Bug filter removed (all resolved tickets, for TLP features)
 */
record JiraProjectState(
        String baseUrl,
        String authHeader,
        String searchBase,
        String allTicketsSearchBase
) {
    /**
     * Builds a Jira session from the loaded properties and the selected project key.
     *
     * @param properties loaded Jira properties
     * @param projectKey Jira project key
     * @return initialized Jira project session
     * @throws JiraClientException when required properties are missing or the search URL cannot be built
     */
    static JiraProjectState fromProperties(Properties properties, String projectKey) throws JiraClientException {
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
            String allTicketsSearchBase = new URIBuilder(baseUrl + "/rest/api/2/search")
                    .addParameter("jql", deriveAllTypesQuery(queryTemplate).replace("{projectKey}", projectKey))
                    .build()
                    .toString();
            return new JiraProjectState(baseUrl, "Bearer " + pat, searchBase, allTicketsSearchBase);
        } catch (Exception exception) {
            throw new JiraClientException("JIRA URI construction error", exception);
        }
    }

    /**
     * Derives an all-issue-types JQL by removing the {@code issuetype/type = Bug} clause so that
     * ticket-level (TLP) features can be computed over every resolved ticket, not only bugs.
     *
     * @param jql configured bug-oriented JQL
     * @return JQL without the issue-type Bug restriction
     */
    static String deriveAllTypesQuery(String jql) {
        String s = jql;
        s = s.replaceAll(
                "(?i)AND\\s+(?:issue)?type\\s*=\\s*Bug",
                ""
        );
        s = s.replaceAll("(?i)(issuetype|type)\\s*=\\s*Bug\\s+AND\\s+", "");
        s = s.replaceAll("(?i)(issuetype|type)\\s*=\\s*Bug", "");
        return s.trim();
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
