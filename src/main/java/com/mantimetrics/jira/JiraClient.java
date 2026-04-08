package com.mantimetrics.jira;

import org.apache.http.client.HttpRequestRetryHandler;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class JiraClient {
    private static final Logger LOG = LoggerFactory.getLogger(JiraClient.class);
    private static final String PROPS_PATH =
            System.getProperty("jira.config.path", "/jira.properties");

    @SuppressWarnings("FieldCanBeLocal")
    private final CloseableHttpClient httpClient;
    private final JiraConfigurationLoader configurationLoader;
    private final JiraProjectReader projectReader;
    private JiraProjectSession session;

    public JiraClient() {
        this(buildHttpClient(), new JiraConfigurationLoader());
    }

    JiraClient(CloseableHttpClient httpClient, JiraConfigurationLoader configurationLoader) {
        this.httpClient = httpClient;
        this.configurationLoader = configurationLoader;
        this.projectReader = new JiraProjectReader(new JiraJsonClient(httpClient));
    }

    public void initialize(String projectKey) throws JiraClientException {
        this.session = configurationLoader.load(getClass(), PROPS_PATH, projectKey);
        LOG.debug("JIRA search base = {}", session.searchBase());
    }

    public List<String> fetchBugKeys() throws JiraClientException {
        return projectReader.fetchBugKeys(requireSession());
    }

    public List<String> fetchProjectVersions(String projectKey) throws JiraClientException {
        return projectReader.fetchProjectVersions(requireSession(), projectKey);
    }

    public boolean isMethodBuggy(List<String> commitKeys, List<String> bugKeys) {
        return commitKeys.stream().anyMatch(bugKeys::contains);
    }

    public static String normalize(String value) {
        return JiraProjectSession.normalize(value);
    }

    private JiraProjectSession requireSession() throws JiraClientException {
        if (session == null) {
            throw new JiraClientException("JIRA client not initialized");
        }
        return session;
    }

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

    @SuppressWarnings("unused")
    public CloseableHttpClient getHttpClient() {
        return httpClient;
    }
}
