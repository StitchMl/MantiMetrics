package com.mantimetrics.jira;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;

final class JiraJsonClient {
    private static final ObjectMapper JSON = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final CloseableHttpClient httpClient;

    JiraJsonClient(CloseableHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    JsonNode get(URI url, String authHeader) throws IOException, JiraClientException {
        HttpGet request = new HttpGet(url);
        request.setHeader("Authorization", authHeader);
        request.setHeader("Accept", "application/json");

        try (CloseableHttpResponse response = httpClient.execute(request)) {
            int code = response.getStatusLine().getStatusCode();
            String body = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
            if (code != 200) {
                throw new JiraClientException("JIRA HTTP " + code + " -> " + body);
            }
            return JSON.readTree(body);
        }
    }
}
