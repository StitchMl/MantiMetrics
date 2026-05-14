package com.mantimetrics.sonar;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mantimetrics.util.AnalysisPathUtils;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

/**
 * HTTP client for the SonarCloud public REST API.
 * Authentication is optional: set the {@code SONAR_TOKEN} environment variable for private projects.
 */
public final class SonarCloudClient implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(SonarCloudClient.class);
    private static final String BASE_URL = "https://sonarcloud.io";
    private static final int PAGE_SIZE = 500;
    private static final ObjectMapper JSON = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final CloseableHttpClient httpClient;
    private final String authHeader;

    /**
     * Creates a client that reads the token from the {@code SONAR_TOKEN} environment variable.
     */
    public SonarCloudClient() {
        this(buildHttpClient(), System.getenv("SONAR_TOKEN"));
    }

    /**
     * Creates a client using the supplied token directly (may be {@code null} for public projects).
     *
     * @param token optional Bearer token; may be {@code null} for public projects
     */
    public SonarCloudClient(String token) {
        this(buildHttpClient(), token);
    }

    /**
     * Creates a client with injectable collaborators, mainly for testing.
     *
     * @param httpClient HTTP client to use
     * @param token optional Bearer token; may be {@code null} for public projects
     */
    SonarCloudClient(CloseableHttpClient httpClient, String token) {
        this.httpClient = httpClient;
        this.authHeader = (token != null && !token.isBlank()) ? "Bearer " + token : null;
    }

    /**
     * Fetches all analyses for a SonarCloud project, sorted oldest to newest.
     *
     * @param projectKey SonarCloud project key (e.g. {@code apache_avro})
     * @return ordered list of analyses
     * @throws SonarCloudException when the API call fails
     */
    public List<SonarAnalysis> fetchAnalyses(String projectKey) throws SonarCloudException {
        List<SonarAnalysis> analyses = new ArrayList<>();
        try {
            int page = 1;
            while (true) {
                URI uri = new URIBuilder(BASE_URL + "/api/project_analyses/search")
                        .addParameter("project", projectKey)
                        .addParameter("ps", String.valueOf(PAGE_SIZE))
                        .addParameter("p", String.valueOf(page))
                        .build();
                JsonNode response = get(uri);
                JsonNode analysesNode = response.path("analyses");
                if (!analysesNode.isArray() || analysesNode.isEmpty()) {
                    break;
                }
                for (JsonNode a : analysesNode) {
                    String key     = a.path("key").asText(null);
                    String dateStr = a.path("date").asText(null);
                    if (key != null && dateStr != null) {
                        String version = a.path("projectVersion").asText(null);
                        analyses.add(new SonarAnalysis(key, Instant.parse(dateStr), version));
                    }
                }
                int total = response.path("paging").path("total").asInt(0);
                if (page * PAGE_SIZE >= total) {
                    break;
                }
                page++;
            }
        } catch (IOException | URISyntaxException e) {
            throw new SonarCloudException("Failed to fetch analyses for " + projectKey, e);
        }
        analyses.sort(Comparator.comparing(SonarAnalysis::date));
        LOG.debug("SonarCloud {} - {} analyses fetched", projectKey, analyses.size());
        return analyses;
    }

    /**
     * Fetches file-level {@code code_smells} counts for a specific analysis snapshot.
     * Paths are normalized to match the dataset path format (no leading slash, no module prefix).
     *
     * @param projectKey SonarCloud project key
     * @param analysisKey analysis snapshot key returned by {@link #fetchAnalyses}
     * @return map of normalized relative path → code smell count
     * @throws SonarCloudException when the API call fails
     */
    public Map<String, Integer> fetchFileSmells(String projectKey, String analysisKey)
            throws SonarCloudException {
        Map<String, Integer> result = new LinkedHashMap<>();
        try {
            int page = 1;
            while (true) {
                URIBuilder builder = new URIBuilder(BASE_URL + "/api/measures/component_tree")
                        .addParameter("component", projectKey)
                        .addParameter("metricKeys", "code_smells")
                        .addParameter("strategy", "leaves")
                        .addParameter("ps", String.valueOf(PAGE_SIZE))
                        .addParameter("p", String.valueOf(page));
                if (analysisKey != null) {
                    builder.addParameter("analysisId", analysisKey);
                }
                URI uri = builder.build();
                JsonNode response = get(uri);
                JsonNode components = response.path("components");
                if (!components.isArray() || components.isEmpty()) {
                    break;
                }
                for (JsonNode comp : components) {
                    String rawKey = comp.path("key").asText(null);
                    if (rawKey == null || !rawKey.endsWith(".java")) {
                        continue;
                    }
                    JsonNode measures = comp.path("measures");
                    if (!measures.isArray()) {
                        continue;
                    }
                    for (JsonNode m : measures) {
                        if ("code_smells".equals(m.path("metric").asText())) {
                            String valueStr = m.path("value").asText(null);
                            if (valueStr != null) {
                                result.put(normalizeSonarPath(projectKey, rawKey),
                                        Integer.parseInt(valueStr));
                            }
                        }
                    }
                }
                int total = response.path("paging").path("total").asInt(0);
                if (page * PAGE_SIZE >= total) {
                    break;
                }
                page++;
            }
        } catch (IOException | URISyntaxException e) {
            throw new SonarCloudException("Failed to fetch file smells for " + projectKey
                    + " analysis " + analysisKey, e);
        }
        LOG.debug("SonarCloud {} analysis {} - {} files with smells", projectKey, analysisKey, result.size());
        return result;
    }

    /**
     * Normalizes a SonarCloud component key to the dataset path format.
     * Strips the project prefix and, for multi-module projects, the first module segment.
     *
     * @param projectKey SonarCloud project key
     * @param rawKey component key returned by the API
     * @return normalized relative path matching the dataset path format
     */
    static String normalizeSonarPath(String projectKey, String rawKey) {
        String relative = rawKey.startsWith(projectKey + ":")
                ? rawKey.substring(projectKey.length() + 1) : rawKey;
        // Strip first segment when path does not start with "src/" (multi-module layout)
        if (!relative.startsWith("src/")) {
            int firstSlash = relative.indexOf('/');
            if (firstSlash >= 0) {
                relative = relative.substring(firstSlash + 1);
            }
        }
        return AnalysisPathUtils.normalizeDatasetPath(relative);
    }

    private JsonNode get(URI uri) throws IOException, SonarCloudException {
        HttpGet request = new HttpGet(uri);
        if (authHeader != null) {
            request.setHeader("Authorization", authHeader);
        }
        request.setHeader("Accept", "application/json");
        try (CloseableHttpResponse response = httpClient.execute(request)) {
            int code = response.getStatusLine().getStatusCode();
            String body = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
            if (code != 200) {
                throw new SonarCloudException("SonarCloud HTTP " + code + " for " + uri + " -> " + body);
            }
            return JSON.readTree(body);
        }
    }

    @Override
    public void close() throws IOException {
        httpClient.close();
    }

    private static CloseableHttpClient buildHttpClient() {
        RequestConfig config = RequestConfig.custom()
                .setConnectTimeout(30_000)
                .setSocketTimeout(60_000)
                .build();
        return HttpClients.custom()
                .setDefaultRequestConfig(config)
                .setMaxConnTotal(20)
                .build();
    }
}
