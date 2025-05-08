package com.mantimetrics.jira;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.client.methods.*;
import org.apache.http.impl.client.*;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Fetches *all* “Fixed” bug issues for a JIRA project (paged). */
public class JiraClient {
    private static final Logger log = LoggerFactory.getLogger(JiraClient.class);
    private static final int PAGE_SIZE = 100;

    private String searchBase;
    private String authHeader;

    /** Initialize the JIRA client with the given project key. */
    public void initialize(String projectKey) throws JiraClientException {
        Properties props = new Properties();
        try (InputStream in = getClass().getResourceAsStream("/application.properties")) {
            if (in == null)
                throw new JiraClientException("application.properties missing in class-path");
            props.load(in);
        } catch (IOException io) {
            throw new JiraClientException("Cannot load JIRA properties", io);
        }

        String baseUrl = stripTrailingSlashes(props.getProperty("jira.url", "").trim());
        String pat     = props.getProperty("jira.pat",   "").trim();
        String jqlTpl  = props.getProperty("jira.query", "").trim();
        if (baseUrl.isEmpty() || pat.isEmpty() || jqlTpl.isEmpty())
            throw new JiraClientException("jira.url / jira.pat / jira.query must all be set");

        String jql = URLEncoder.encode(jqlTpl.replace("{projectKey}", projectKey),
                StandardCharsets.UTF_8);
        this.searchBase = baseUrl + "/rest/api/2/search?jql=" + jql;
        this.authHeader = "Bearer " + pat;

        log.debug("JIRA search base = {}", searchBase);
    }

    /** Fetch all bug keys for the given project. */
    public List<String> fetchBugKeys() throws JiraClientException {
        Set<String> keys = new HashSet<>();
        int startAt = 0;

        try (CloseableHttpClient http = HttpClients.createDefault()) {
            while (true) {
                String url = searchBase +
                        "&startAt="    + startAt +
                        "&maxResults=" + PAGE_SIZE;
                JsonNode root = doRequest(http, url);

                JsonNode issues = root.path("issues");
                issues.forEach(n -> Optional.ofNullable(n.path("key").asText(null))
                        .ifPresent(keys::add));

                startAt += PAGE_SIZE;
                if (startAt >= root.path("total").asInt() || issues.isEmpty())
                    break;
            }
        } catch (IOException io) {
            throw new JiraClientException("I/O error talking to JIRA", io);
        }

        log.trace("Fetched {} bug keys from JIRA", keys.size());
        return new ArrayList<>(keys);
    }

    /** True if *any* key in the commit message also appears among JIRA bug keys. */
    public boolean isMethodBuggy(List<String> commitKeys, List<String> bugKeys) {
        return commitKeys.stream().anyMatch(bugKeys::contains);
    }

    /** True if *any* key in the commit message also appears among JIRA bug keys. */
    private JsonNode doRequest(CloseableHttpClient http, String url)
            throws IOException, JiraClientException {

        HttpGet get = new HttpGet(url);
        get.setHeader("Authorization", authHeader);
        get.setHeader("Accept", "application/json");

        try (CloseableHttpResponse resp = http.execute(get)) {
            int code = resp.getStatusLine().getStatusCode();
            if (code != 200)
                throw new JiraClientException("JIRA search failed HTTP " + code);
            String body = EntityUtils.toString(resp.getEntity(), StandardCharsets.UTF_8);
            return new ObjectMapper().readTree(body);
        }
    }

    /** O(n) removal of trailing ‘/’—no regex, no back-tracking vulnerabilities. */
    private static String stripTrailingSlashes(String s) {
        int end = s.length();
        while (end > 0 && s.charAt(end - 1) == '/') end--;
        return s.substring(0, end);
    }
}