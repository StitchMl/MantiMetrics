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

/**
 * Fetches **all** fixed-bug issues for a project, transparently paging the
 * JIRA Search REST API (default page size = 50).
 */
public class JiraClient {
    private static final Logger log = LoggerFactory.getLogger(JiraClient.class);
    private static final int PAGE_SIZE = 100;

    private String searchBase;
    private String authHeader;

    /* --------------------------------------------------- init -------- */

    public void initialize(String projectKey) throws JiraClientException {
        Properties p = new Properties();
        try (InputStream in = getClass().getResourceAsStream("/application.properties")) {
            if (in == null) throw new JiraClientException("application.properties missing");
            p.load(in);
        } catch (IOException e) {
            throw new JiraClientException("Error loading JIRA config", e);
        }

        String base  = p.getProperty("jira.url" ).trim().replaceAll("/+$", "");
        String pat   = p.getProperty("jira.pat" ).trim();
        String jqlT  = p.getProperty("jira.query").trim();
        if (base.isEmpty() || pat.isEmpty() || jqlT.isEmpty())
            throw new JiraClientException("jira.url / jira.pat / jira.query must be set");

        String jql = URLEncoder.encode(jqlT.replace("{projectKey}", projectKey),
                StandardCharsets.UTF_8);
        this.searchBase = base + "/rest/api/2/search?jql=" + jql;
        this.authHeader = "Bearer " + pat;

        log.debug("JIRA search base = {}", searchBase);
    }

    /* ----------------------------------------------- public API ------ */

    public List<String> fetchBugKeys() throws JiraClientException {
        var keys = new HashSet<String>();
        int startAt = 0;

        try (CloseableHttpClient http = HttpClients.createDefault()) {
            while (true) {
                String url = searchBase +
                        "&startAt="  + startAt +
                        "&maxResults=" + PAGE_SIZE;
                JsonNode root = execute(http, url);

                int total     = root.path("total").asInt(0);
                JsonNode issues = root.path("issues");
                issues.forEach(n -> {
                    String k = n.path("key").asText(null);
                    if (k != null) keys.add(k);
                });

                startAt += PAGE_SIZE;
                if (startAt >= total || issues.isEmpty()) break;
            }
        } catch (IOException io) {
            throw new JiraClientException("I/O error talking to JIRA", io);
        }

        log.trace("Fetched {} bug keys from JIRA", keys.size());
        return new ArrayList<>(keys);
    }

    public boolean isMethodBuggy(List<String> commitKeys, List<String> bugKeys) {
        return commitKeys.stream().anyMatch(bugKeys::contains);
    }

    /* ----------------------------------------------- helpers --------- */

    private JsonNode execute(CloseableHttpClient http, String url) throws IOException, JiraClientException {
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
}