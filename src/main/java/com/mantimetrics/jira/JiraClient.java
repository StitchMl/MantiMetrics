package com.mantimetrics.jira;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.client.HttpRequestRetryHandler;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.*;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.*;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Client for JIRA: retrieves all 'Fixed' Bug issues in paginated mode.
 */
public class JiraClient {
    private static final Logger log = LoggerFactory.getLogger(JiraClient.class);
    private static final String PROPS_PATH =
            System.getProperty("jira.config.path", "/jira.properties");
    private static final ObjectMapper JSON = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);


    private static final int PAGE_SIZE = 100;
    private final CloseableHttpClient http;

    private String searchBase;
    private String authHeader;

    public JiraClient() {
        // Configuring retry handlers for transient errors
        HttpRequestRetryHandler retryHandler = (exception, executionCount, context) ->
                executionCount < 3 && exception != null;

        RequestConfig reqConfig = RequestConfig.custom()
                .setConnectTimeout(30_000)
                .setSocketTimeout(30_000)
                .build();

        this.http = HttpClients.custom()
                .setDefaultRequestConfig(reqConfig)
                .setRetryHandler(retryHandler)
                .setMaxConnTotal(50)
                .build();
    }

    /**
     * Initialize the client with the JIRA project.
     */
    public void initialize(String projectKey) throws JiraClientException {
        Properties props = new Properties();
        try (InputStream in = getClass().getResourceAsStream(PROPS_PATH)) {
            if (in == null)
                throw new JiraClientException("JIRA configuration file not found: " + PROPS_PATH);
            props.load(in);
        } catch (IOException io) {
            throw new JiraClientException("Error loading JIRA properties", io);
        }

        String baseUrl = stripTrailingSlashes(props.getProperty("jira.url", "").trim());
        String pat     = props.getProperty("jira.pat",   "").trim();
        String jqlTpl  = props.getProperty("jira.query", "").trim();
        if (baseUrl.isEmpty() || pat.isEmpty() || jqlTpl.isEmpty())
            throw new JiraClientException("jira.url, jira.pat and jira.query must be valorised");

        // Constructing URLs securely with URIBuilder
        try {
            URI uri = new URIBuilder(baseUrl + "/rest/api/2/search")
                    .addParameter("jql", jqlTpl.replace("{projectKey}", projectKey))
                    .build();
            this.searchBase = uri.toString();
        } catch (Exception e) {
            throw new JiraClientException("JIRA URI construction error", e);
        }

        this.authHeader = "Bearer " + pat;
        log.debug("JIRA search base = {}", searchBase);
    }

    /**
     * Retrieve all Fixed bug keys by paging.
     */
    public List<String> fetchBugKeys() throws JiraClientException {
        Set<String> keys = new HashSet<>();
        int startAt = 0;

        try {
            while (true) {
                URI uri = new URIBuilder(searchBase)
                        .addParameter("startAt", String.valueOf(startAt))
                        .addParameter("maxResults", String.valueOf(PAGE_SIZE))
                        .build();

                var root = doRequest(http, uri);

                root.path("issues")
                        .forEach(n -> Optional.ofNullable(n.path("key").asText(null))
                                .ifPresent(keys::add));

                int total = root.path("total").asInt();
                startAt += PAGE_SIZE;
                if (startAt >= total) break;
            }
        } catch (IOException e) {
            throw new JiraClientException("I/O Error to JIRA", e);
        } catch (Exception e) {
            throw new JiraClientException("Error fetchBugKeys", e);
        }

        log.trace("Fetched {} bug keys from JIRA", keys.size());
        return new ArrayList<>(keys);
    }

    /** True if *any* key in the commit message also appears among JIRA bug keys. */
    public boolean isMethodBuggy(List<String> commitKeys, List<String> bugKeys) {
        return commitKeys.stream().anyMatch(bugKeys::contains);
    }

    /** True if *any* key in the commit message also appears among JIRA bug keys. */
    private JsonNode doRequest(CloseableHttpClient http, URI url)
            throws IOException, JiraClientException {

        HttpGet get = new HttpGet(url);
        get.setHeader("Authorization", authHeader);
        get.setHeader("Accept", "application/json");

        try (CloseableHttpResponse resp = http.execute(get)) {
            int code = resp.getStatusLine().getStatusCode();
            String body = EntityUtils.toString(resp.getEntity(), StandardCharsets.UTF_8);
            if (code != 200){
                throw new JiraClientException("JIRA HTTP " + code + " → " + body);
            }
            return JSON.readTree(body);
        }
    }

    /** Removes final slashes without an expensive regex. */
    private static String stripTrailingSlashes(String s) {
        int end = s.length();
        while (end > 0 && s.charAt(end - 1) == '/') end--;
        return s.substring(0, end);
    }
}