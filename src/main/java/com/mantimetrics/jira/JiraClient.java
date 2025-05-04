package com.mantimetrics.jira;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class JiraClient {
    private String searchEndpoint;
    private String authHeader;

    /**
     * Initializes the JIRA client using the PAT as Bearer token.
     *
     * @param projectKey JIRA project key (e.g. "BOOKKEEPER")
     */
    public void initialize(String projectKey) throws Exception {
        Properties props = new Properties();
        try (InputStream in = getClass().getResourceAsStream("/application.properties")) {
            props.load(in);
        }

        // Endpoint and header construction
        String baseUrl = props.getProperty("jira.url").replaceAll("/+$", "");
        String pat     = props.getProperty("jira.pat");
        String jqlTempl= props.getProperty("jira.query");

        // Mount the JQL by replacing {projectKey} and url-encode
        String jql = jqlTempl.replace("{projectKey}", projectKey);
        System.out.println("DEBUG: JQL raw -> " + jql);  // log per debug :contentReference[oaicite:2]{index=2}
        String encodedJql = URLEncoder.encode(jql, StandardCharsets.UTF_8);

        this.searchEndpoint = baseUrl + "/rest/api/2/search?jql=" + encodedJql;
        this.authHeader     = "Bearer " + pat;           // Bearer Auth with PAT: contentReference[oaicite:3]{index=3}
    }

    /**
     * Runs JIRA query, checks HTTP 200, then extracts issues[].key.
     *
     * @return list of issue keys (e.g. ["BOOKKEEPER-123", ...])
     */
    public List<String> fetchBugKeys() throws Exception {
        List<String> keys = new ArrayList<>();

        System.out.println("DEBUG: Searching JIRA issues for project: " + this.searchEndpoint);
        try (CloseableHttpClient client = HttpClients.createDefault()) {
            HttpGet get = new HttpGet(this.searchEndpoint);
            get.setHeader("Authorization", this.authHeader);
            get.setHeader("Accept", "application/json");

            System.out.println("DEBUG: HTTP GET: " + get.getRequestLine());
            try (CloseableHttpResponse resp = client.execute(get)) {
                int status = resp.getStatusLine().getStatusCode();
                String body = EntityUtils.toString(resp.getEntity(), StandardCharsets.UTF_8);

                // HTTP check before parse JSON: contentReference[oaicite:4]{index=4}
                if (status != 200) {
                    throw new RuntimeException("JIRA API Error: HTTP " + status + "\nResponse: " + body);
                }

                // Parse JSON issues[].key
                JsonNode root   = new ObjectMapper().readTree(body);
                JsonNode issues = root.path("issues");
                if (issues.isArray()) {
                    for (JsonNode issue : issues) {
                        JsonNode keyNode = issue.get("key");
                        if (keyNode != null) {
                            keys.add(keyNode.asText());
                        }
                    }
                }
            }
        }
        System.out.println("DEBUG: Found " + keys.size() + " JIRA issues.");
        return keys;
    }

    /**
     * Checks whether a method is buggy by comparing commitHashes with bugKeys.
     */
    public boolean isMethodBuggy(List<String> commitIssueKeys, List<String> bugKeys) {
        // bugKeys is the list of JIRA issues resolved with 'Fixed'.
        return commitIssueKeys.stream().anyMatch(bugKeys::contains);
    }
}