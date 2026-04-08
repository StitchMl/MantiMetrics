package com.mantimetrics.jira;

import org.apache.http.client.utils.URIBuilder;

import java.util.Locale;
import java.util.Properties;

record JiraProjectSession(
        String baseUrl,
        String authHeader,
        String searchBase
) {
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

    static String normalize(String value) {
        return value.toLowerCase(Locale.ROOT)
                .replaceFirst("^(release-|rel-|ver-|v)", "")
                .trim();
    }

    private static String stripTrailingSlashes(String value) {
        int end = value.length();
        while (end > 0 && value.charAt(end - 1) == '/') {
            end--;
        }
        return value.substring(0, end);
    }
}
