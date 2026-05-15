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
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAccessor;
import java.util.*;

/**
 * HTTP client for the SonarCloud public REST API.
 * Authentication is optional: set the {@code SONAR_TOKEN} environment variable for private projects.
 */
@SuppressWarnings("unused")
public final class SonarCloudClient implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(SonarCloudClient.class);
    private static final String BASE_URL = "https://sonarcloud.io";
    private static final int PAGE_SIZE = 500;
    private static final ObjectMapper JSON = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    /**
     * Handles both ISO-8601 ({@code Z}) and SonarCloud's {@code +0000} offset format
     * (missing colon between hours and minutes).
     */
    private static final DateTimeFormatter SONAR_DATE =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ");

    private final CloseableHttpClient httpClient;
    private final String authHeader;

    /**
     * Creates a client that reads the token from the {@code SONAR_TOKEN} environment variable.
     */
    public SonarCloudClient() {
        this(buildHttpClient(), System.getenv("SONAR_TOKEN"));
    }

    /**
     * Creates a client using the supplied token directly (maybe {@code null} for public projects).
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
            boolean hasMore = true;
            while (hasMore) {
                URI uri = new URIBuilder(BASE_URL + "/api/project_analyses/search")
                        .addParameter("project", projectKey)
                        .addParameter("ps", String.valueOf(PAGE_SIZE))
                        .addParameter("p", String.valueOf(page))
                        .build();
                JsonNode response = get(uri);
                JsonNode analysesNode = response.path("analyses");
                if (!analysesNode.isArray() || analysesNode.isEmpty()) {
                    hasMore = false;
                } else {
                    parseAnalysesPage(analysesNode, analyses);
                    int total = response.path("paging").path("total").asInt(0);
                    if (page * PAGE_SIZE >= total) {
                        hasMore = false;
                    } else {
                        page++;
                    }
                }
            }
        } catch (IOException | URISyntaxException e) {
            throw new SonarCloudException("Failed to fetch analyses for " + projectKey, e);
        }
        analyses.sort(Comparator.comparing(SonarAnalysis::date));
        LOG.debug("SonarCloud {} - {} analyses fetched", projectKey, analyses.size());
        return analyses;
    }

    /**
     * Parses one page of analysis nodes and appends valid entries to the output list.
     *
     * @param analysesNode JSON array of analysis nodes
     * @param analyses     output list to append to
     */
    private static void parseAnalysesPage(JsonNode analysesNode, List<SonarAnalysis> analyses) {
        for (JsonNode a : analysesNode) {
            String key     = a.path("key").asText(null);
            String dateStr = a.path("date").asText(null);
            if (key != null && dateStr != null) {
                String version = a.path("projectVersion").asText(null);
                analyses.add(new SonarAnalysis(key, parseSonarDate(dateStr), version));
            }
        }
    }

    /**
     * Fetches file-level {@code code_smells} counts for a specific analysis snapshot.
     * Paths are normalized to match the dataset path format (no leading or trailing slashes;
     * the repository-relative path segments are kept verbatim).
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
            boolean hasMore = true;
            while (hasMore) {
                URI uri = buildComponentTreeUri(projectKey, analysisKey, page);
                JsonNode response = get(uri);
                JsonNode components = response.path("components");
                if (!components.isArray() || components.isEmpty()) {
                    hasMore = false;
                } else {
                    parseComponents(components, projectKey, result);
                    int total = response.path("paging").path("total").asInt(0);
                    if (page * PAGE_SIZE >= total) {
                        hasMore = false;
                    } else {
                        page++;
                    }
                }
            }
        } catch (IOException | URISyntaxException e) {
            throw new SonarCloudException("Failed to fetch file smells for " + projectKey
                    + " analysis " + analysisKey, e);
        }
        LOG.debug("SonarCloud {} analysis {} - {} files with smells", projectKey, analysisKey, result.size());
        return result;
    }

    /**
     * Builds the URI for one page of the component-tree endpoint.
     *
     * @param projectKey  SonarCloud project key
     * @param analysisKey optional analysis snapshot key
     * @param page        1-based page number
     * @return constructed URI
     * @throws URISyntaxException when URI construction fails
     */
    private URI buildComponentTreeUri(String projectKey, String analysisKey, int page)
            throws URISyntaxException {
        URIBuilder builder = new URIBuilder(BASE_URL + "/api/measures/component_tree")
                .addParameter("component", projectKey)
                .addParameter("metricKeys", "code_smells")
                .addParameter("strategy", "leaves")
                .addParameter("ps", String.valueOf(PAGE_SIZE))
                .addParameter("p", String.valueOf(page));
        if (analysisKey != null) {
            builder.addParameter("analysisId", analysisKey);
        }
        return builder.build();
    }

    /**
     * Parses one page of component nodes and appends Java-file entries to the result map.
     *
     * @param components JSON array of component nodes
     * @param projectKey SonarCloud project key used for path normalization
     * @param result     output map to append to
     */
    private void parseComponents(JsonNode components, String projectKey, Map<String, Integer> result) {
        for (JsonNode comp : components) {
            String rawKey = comp.path("key").asText(null);
            if (rawKey != null && rawKey.endsWith(".java")) {
                Integer smellValue = extractSmellValue(comp.path("measures"));
                if (smellValue != null) {
                    result.put(normalizeSonarPath(projectKey, rawKey), smellValue);
                }
            }
        }
    }

    /**
     * Extracts the {@code code_smells} integer value from a component's measures array.
     *
     * @param measures JSON array of measure nodes for a component
     * @return the code-smell count, or {@code null} when the metric is absent
     */
    private static Integer extractSmellValue(JsonNode measures) {
        if (!measures.isArray()) {
            return null;
        }
        for (JsonNode m : measures) {
            if ("code_smells".equals(m.path("metric").asText())) {
                String valueStr = m.path("value").asText(null);
                if (valueStr != null) {
                    return Integer.parseInt(valueStr);
                }
            }
        }
        return null;
    }

    /**
     * Normalizes a SonarCloud component key to the dataset path format.
     * Strips the {@code projectKey:} prefix and then applies the canonical
     * dataset-path normalization (removes any leading/trailing slashes).
     * The path segments after the prefix are kept verbatim so they match
     * the repository-relative paths stored in dataset rows.
     *
     * @param projectKey SonarCloud project key
     * @param rawKey component key returned by the API
     * @return normalized relative path matching the dataset path format
     */
    static String normalizeSonarPath(String projectKey, String rawKey) {
        String relative = rawKey.startsWith(projectKey + ":")
                ? rawKey.substring(projectKey.length() + 1) : rawKey;
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

    /**
     * Parses a SonarCloud date string that may use either the standard ISO-8601 {@code Z} suffix
     * or the non-standard {@code +0000} / {@code +0200} offset (no colon between HH and MM).
     *
     * @param dateStr date string from the SonarCloud API
     * @return parsed instant
     */
    static Instant parseSonarDate(String dateStr) {
        try {
            return Instant.parse(dateStr);                              // standard "Z" form
        } catch (DateTimeParseException ignored) {
            TemporalAccessor ta = SONAR_DATE.parse(dateStr);           // "+0000" / "+0200" form
            return Instant.from(ta);
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
