package com.mantimetrics.jira;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Client to query JIRA and get the keys of the resolved bugs.
 */
public class JiraClient {
    private static final Logger logger = LoggerFactory.getLogger(JiraClient.class);

    private String searchEndpoint;
    private String authHeader;

    /**
     * Initializes the JIRA client using PAT as Bearer token.
     *
     * @param projectKey JIRA project key (e.g. "BOOKKEEPER")
     * @throws JiraClientException in case of configuration problems
     */
    public void initialize(String projectKey) throws JiraClientException {
        logger.info("Initializing JiraClient for project '{}'", projectKey);

        Properties props = new Properties();
        try (InputStream in = getClass().getResourceAsStream("/application.properties")) {
            if (in == null) {
                throw new JiraClientException("application.properties not found in classpath");
            }
            props.load(in);
            logger.debug("Loaded JIRA configuration properties");
        } catch (IOException e) {
            throw new JiraClientException("Error loading JIRA configuration", e);
        }

        String rawUrl   = props.getProperty("jira.url", "").trim();
        String baseUrl  = stripTrailingSlashes(rawUrl);
        String pat      = props.getProperty("jira.pat", "").trim();
        String jqlTempl = props.getProperty("jira.query", "").trim();

        if (baseUrl.isEmpty() || pat.isEmpty() || jqlTempl.isEmpty()) {
            throw new JiraClientException("Missing or invalid JIRA properties");
        }

        String jql = jqlTempl.replace("{projectKey}", projectKey);
        String encodedJql;
        try {
            encodedJql = URLEncoder.encode(jql, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new JiraClientException("JQL encoding error", e);
        }

        this.searchEndpoint = baseUrl + "/rest/api/2/search?jql=" + encodedJql;
        this.authHeader     = "Bearer " + pat;

        logger.debug("JIRA search endpoint set to {}", searchEndpoint);
    }

    /**
     * Executes the JQL and returns the issue-keys of the resolved bugs.
     *
     * @return list of issue keys (e.g. ["BOOKKEEPER-123", ...])
     * @throws JiraClientException in case of HTTP or parsing error
     */
    public List<String> fetchBugKeys() throws JiraClientException {
        logger.info("Fetching bug keys from JIRA");
        logger.trace("Sending GET {}", searchEndpoint);

        List<String> keys = new ArrayList<>();
        try (CloseableHttpClient client = HttpClients.createDefault()) {
            HttpGet get = new HttpGet(this.searchEndpoint);
            get.setHeader("Authorization", this.authHeader);
            get.setHeader("Accept", "application/json");

            try (CloseableHttpResponse resp = client.execute(get)) {
                int status = resp.getStatusLine().getStatusCode();
                String body = EntityUtils.toString(resp.getEntity(), StandardCharsets.UTF_8);

                if (status != 200) {
                    throw new JiraClientException("JIRA API Error: HTTP " + status);
                }

                JsonNode root   = new ObjectMapper().readTree(body);
                JsonNode issues = root.path("issues");
                if (!issues.isArray()) {
                    throw new JiraClientException("'issues' not an array in JIRA response");
                }

                for (JsonNode issue : issues) {
                    JsonNode keyNode = issue.get("key");
                    if (keyNode != null && keyNode.isTextual()) {
                        keys.add(keyNode.asText());
                    }
                }

                logger.info("Retrieved {} bug keys from JIRA", keys.size());
            }
        } catch (IOException e) {
            throw new JiraClientException("I/O error communicating with JIRA", e);
        }

        return keys;
    }

    /**
     * Determines if a method is buggy by comparing JIRA keys from commits
     * with those of the resolved bugs.
     *
     * @param commitIssueKeys JIRA keys found in commit messages
     * @param bugKeys list of JIRA keys from bug fixes
     * @return true if at least one match
     */
    public boolean isMethodBuggy(List<String> commitIssueKeys, List<String> bugKeys) {
        boolean buggy = commitIssueKeys.stream().anyMatch(bugKeys::contains);
        logger.trace("MethodBuggy? {} (commitKeys={} bugKeys={})",
                buggy, commitIssueKeys, bugKeys);
        return buggy;
    }

    /**
     * Remove all trailing '/' characters in O(n) time with no backtracking.
     */
    private static String stripTrailingSlashes(String url) {
        if (url == null || url.isEmpty()) return url;
        int end = url.length();
        while (end > 0 && url.charAt(end - 1) == '/') {
            end--;
        }
        return url.substring(0, end);
    }
}