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

import java.io.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class JiraClient {
    private static final Logger logger = LoggerFactory.getLogger(JiraClient.class);

    private String searchEndpoint;
    private String authHeader;

    public void initialize(String projectKey) throws JiraClientException {
        logger.info("Initializing JiraClient for project '{}'", projectKey);
        Properties props = new Properties();
        try (InputStream in = getClass().getResourceAsStream("/application.properties")) {
            if (in == null) throw new JiraClientException("application.properties missing");
            props.load(in);
        } catch (IOException e) {
            throw new JiraClientException("Error loading JIRA config", e);
        }

        String base  = props.getProperty("jira.url",     "").trim().replaceAll("/+$", "");
        String pat   = props.getProperty("jira.pat",     "").trim();
        String query = props.getProperty("jira.query",   "").trim();
        if (base.isEmpty() || pat.isEmpty() || query.isEmpty()) {
            throw new JiraClientException("Invalid JIRA properties");
        }

        String jql = query.replace("{projectKey}", projectKey);
        try {
            jql = URLEncoder.encode(jql, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new JiraClientException("JQL encoding failed", e);
        }

        this.searchEndpoint = base + "/rest/api/2/search?jql=" + jql;
        this.authHeader     = "Bearer " + pat;
        logger.debug("JIRA endpoint = {}", searchEndpoint);
    }

    public List<String> fetchBugKeys() throws JiraClientException {
        logger.info("Fetching bug keys from JIRA");
        List<String> keys = new ArrayList<>();

        try (CloseableHttpClient client = HttpClients.createDefault()) {
            HttpGet get = new HttpGet(searchEndpoint);
            get.setHeader("Authorization", authHeader);
            get.setHeader("Accept", "application/json");

            try (CloseableHttpResponse resp = client.execute(get)) {
                int status = resp.getStatusLine().getStatusCode();
                String body = EntityUtils.toString(resp.getEntity(), StandardCharsets.UTF_8);
                if (status != 200) {
                    throw new JiraClientException("JIRA API error HTTP " + status);
                }

                JsonNode root = new ObjectMapper().readTree(body);
                JsonNode issues = root.path("issues");
                if (!issues.isArray()) {
                    throw new JiraClientException("Unexpected JIRA response");
                }
                for (JsonNode issue : issues) {
                    JsonNode k = issue.get("key");
                    if (k != null && k.isTextual()) keys.add(k.asText());
                }
                logger.info("Retrieved {} bug keys", keys.size());
            }
        } catch (IOException e) {
            throw new JiraClientException("I/O error talking to JIRA", e);
        }

        return keys;
    }

    public boolean isMethodBuggy(List<String> commitKeys, List<String> bugKeys) {
        boolean buggy = commitKeys.stream().anyMatch(bugKeys::contains);
        logger.trace("MethodBuggy? {} (commitKeys={}, bugKeys.size={})",
                buggy, commitKeys, bugKeys.size());
        return buggy;
    }
}