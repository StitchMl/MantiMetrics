package com.mantimetrics.jira;

import org.apache.http.client.HttpRequestRetryHandler;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Thin JIRA facade responsible for authentication, pagination setup and project-scoped queries.
 */
public class JiraFacade {
    private static final Logger LOG = LoggerFactory.getLogger(JiraFacade.class);
    private static final String PROPS_PATH =
            System.getProperty("jira.config.path", "/jira.properties");

    private final CloseableHttpClient httpClient;
    private final JiraConfigLoader configurationLoader;
    private final JiraTicketReader projectReader;
    private JiraProjectState session;

    /**
     * Creates a Jira client with the default HTTP client and configuration loader.
     */
    public JiraFacade() {
        this(buildHttpClient(), new JiraConfigLoader());
    }

    /**
     * Creates a Jira client with injectable collaborators, mainly for testing.
     *
     * @param httpClient HTTP client used for Jira requests
     * @param configurationLoader loader used to create project sessions from properties
     */
    JiraFacade(CloseableHttpClient httpClient, JiraConfigLoader configurationLoader) {
        this.httpClient = httpClient;
        this.configurationLoader = configurationLoader;
        this.projectReader = new JiraTicketReader(new JiraClient(httpClient));
    }

    /**
     * Initializes the Jira session for a project key using the configured properties.
     *
     * @param projectKey Jira project key
     * @throws JiraClientException when the Jira configuration cannot be loaded
     */
    public void initialize(String projectKey) throws JiraClientException {
        this.session = configurationLoader.load(getClass(), PROPS_PATH, projectKey);
        LOG.debug("JIRA search base = {}", session.searchBase());
    }

    /**
     * Fetches the bug issue keys visible through the configured Jira query.
     *
     * @return Jira bug keys
     * @throws JiraClientException when the session is missing or Jira cannot be queried
     */
    @SuppressWarnings("unused")
    public List<String> fetchBugKeys() throws JiraClientException {
        return projectReader.fetchBugKeys(requireSession());
    }

    /**
     * Fetches the resolved bug tickets used by the historical labeling flow.
     *
     * @return resolved Jira bug tickets
     * @throws JiraClientException when the session is missing or Jira cannot be queried
     */
    public List<JiraSnapshot> fetchResolvedBugTickets() throws JiraClientException {
        return projectReader.fetchResolvedBugTickets(requireSession());
    }

    /**
     * Fetches the normalized versions configured for a Jira project.
     *
     * @param projectKey Jira project key
     * @return normalized project versions
     * @throws JiraClientException when the session is missing or Jira cannot be queried
     */
    public List<String> fetchProjectVersions(String projectKey) throws JiraClientException {
        return projectReader.fetchProjectVersions(requireSession(), projectKey);
    }

    /**
     * Fetches the release dates for all JIRA versions that carry a {@code releaseDate} field.
     *
     * @param projectKey Jira project key
     * @return map of normalized version name to release date instant
     * @throws JiraClientException when the session is missing or Jira cannot be queried
     */
    public Map<String, Instant> fetchProjectVersionDates(String projectKey) throws JiraClientException {
        return projectReader.fetchVersionDates(requireSession(), projectKey);
    }

    /**
     * Reports whether any commit-linked issue key belongs to the bug-key set.
     *
     * @param commitKeys issue keys linked to a code entity
     * @param bugKeys resolved bug keys from Jira
     * @return {@code true} when the entity is linked to at least one bug key
     */
    @SuppressWarnings("unused")
    public boolean isMethodBuggy(List<String> commitKeys, List<String> bugKeys) {
        return commitKeys.stream().anyMatch(bugKeys::contains);
    }

    /**
     * Normalizes tags or version names using the shared Jira normalization rules.
     *
     * @param value raw tag or version identifier
     * @return normalized identifier
     */
    public static String normalize(String value) {
        return JiraProjectState.normalize(value);
    }

    /**
     * Returns the current Jira session or fails when the client was not initialized.
     *
     * @return initialized Jira project session
     * @throws JiraClientException when the client was not initialized
     */
    private JiraProjectState requireSession() throws JiraClientException {
        if (session == null) {
            throw new JiraClientException("JIRA client not initialized");
        }
        return session;
    }

    /**
     * Builds the default HTTP client used for Jira requests.
     *
     * @return configured Jira HTTP client
     */
    private static CloseableHttpClient buildHttpClient() {
        HttpRequestRetryHandler retryHandler = (exception, executionCount, context) ->
                executionCount < 3 && exception != null;

        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(30_000)
                .setSocketTimeout(30_000)
                .build();

        return HttpClients.custom()
                .setDefaultRequestConfig(requestConfig)
                .setRetryHandler(retryHandler)
                .setMaxConnTotal(50)
                .build();
    }

    /**
     * Exposes the underlying HTTP client, mainly for testing.
     *
     * @return Jira HTTP client
     */
    @SuppressWarnings("unused")
    public CloseableHttpClient getHttpClient() {
        return httpClient;
    }
}
