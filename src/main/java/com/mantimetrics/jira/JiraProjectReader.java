package com.mantimetrics.jira;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.http.client.utils.URIBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Reads Jira project data such as bug keys, versions and resolved bug tickets.
 */
final class JiraProjectReader {
    private static final Logger LOG = LoggerFactory.getLogger(JiraProjectReader.class);
    private static final int PAGE_SIZE = 100;

    private final JiraJsonClient jsonClient;

    /**
     * Creates a project reader backed by the shared Jira JSON client.
     *
     * @param jsonClient Jira JSON client used for REST calls
     */
    JiraProjectReader(JiraJsonClient jsonClient) {
        this.jsonClient = jsonClient;
    }

    /**
     * Fetches all bug issue keys matching the configured Jira search.
     *
     * @param session initialized Jira project session
     * @return bug keys returned by Jira
     * @throws JiraClientException when Jira cannot be queried
     */
    List<String> fetchBugKeys(JiraProjectSession session) throws JiraClientException {
        Set<String> keys = new HashSet<>();
        int startAt = 0;

        try {
            while (true) {
                URI uri = new URIBuilder(session.searchBase())
                        .addParameter("startAt", String.valueOf(startAt))
                        .addParameter("maxResults", String.valueOf(PAGE_SIZE))
                        .build();
                JsonNode response = jsonClient.get(uri, session.authHeader());
                collectIssueKeys(response.path("issues"), keys);

                int total = response.path("total").asInt();
                LOG.debug("JIRA total: {} startAt: {}", total, startAt);
                startAt += PAGE_SIZE;
                if (startAt >= total) {
                    break;
                }
            }
        } catch (IOException exception) {
            throw new JiraClientException("I/O Error to JIRA", exception);
        } catch (Exception exception) {
            throw new JiraClientException("Error fetchBugKeys", exception);
        }

        return new ArrayList<>(keys);
    }

    /**
     * Fetches the normalized version names defined for a Jira project.
     *
     * @param session initialized Jira project session
     * @param projectKey Jira project key
     * @return normalized Jira version names
     * @throws JiraClientException when Jira cannot be queried
     */
    List<String> fetchProjectVersions(JiraProjectSession session, String projectKey) throws JiraClientException {
        try {
            URI uri = new URIBuilder(session.baseUrl() + "/rest/api/2/project/" + projectKey + "/versions").build();
            JsonNode versions = jsonClient.get(uri, session.authHeader());
            if (!versions.isArray()) {
                throw new JiraClientException("Unexpected response for project versions");
            }

            List<String> names = new ArrayList<>();
            versions.forEach(version -> {
                String name = version.path("name").asText(null);
                if (name != null && !name.isBlank()) {
                    names.add(JiraProjectSession.normalize(name));
                }
            });

            LOG.debug("JIRA project {} - {} versions fetched", projectKey, names.size());
            return names;
        } catch (IOException exception) {
            throw new JiraClientException("I/O error fetching project versions", exception);
        } catch (Exception exception) {
            throw new JiraClientException("fetchProjectVersions error", exception);
        }
    }

    /**
     * Fetches the resolved bug tickets used by the historical labeling flow.
     *
     * @param session initialized Jira project session
     * @return resolved Jira bug tickets
     * @throws JiraClientException when Jira cannot be queried
     */
    List<JiraBugTicket> fetchResolvedBugTickets(JiraProjectSession session) throws JiraClientException {
        List<JiraBugTicket> tickets = new ArrayList<>();
        int startAt = 0;

        try {
            while (true) {
                URI uri = new URIBuilder(session.searchBase())
                        .addParameter("fields", "key,versions,created")
                        .addParameter("startAt", String.valueOf(startAt))
                        .addParameter("maxResults", String.valueOf(PAGE_SIZE))
                        .build();
                JsonNode response = jsonClient.get(uri, session.authHeader());
                collectTickets(response.path("issues"), tickets);

                int total = response.path("total").asInt();
                startAt += PAGE_SIZE;
                if (startAt >= total) {
                    break;
                }
            }
        } catch (IOException exception) {
            throw new JiraClientException("I/O Error to JIRA", exception);
        } catch (Exception exception) {
            throw new JiraClientException("Error fetchResolvedBugTickets", exception);
        }

        return List.copyOf(tickets);
    }

    /**
     * Extracts issue keys from a Jira search result page.
     *
     * @param issues Jira issues array
     * @param keys target set receiving the keys
     */
    private void collectIssueKeys(JsonNode issues, Set<String> keys) {
        for (JsonNode issue : issues) {
            String key = issue.path("key").asText(null);
            if (key != null) {
                keys.add(key);
            }
        }
    }

    private static final DateTimeFormatter JIRA_DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ");

    /**
     * Extracts the minimal bug-ticket information needed by the labeling flow.
     *
     * @param issues Jira issues array
     * @param tickets target list receiving the extracted bug tickets
     */
    private void collectTickets(JsonNode issues, List<JiraBugTicket> tickets) {
        for (JsonNode issue : issues) {
            String key = issue.path("key").asText(null);
            if (key == null || key.isBlank()) {
                continue;
            }

            String createdRaw = issue.path("fields").path("created").asText(null);
            Instant createdDate = (createdRaw != null && !createdRaw.isBlank())
                    ? OffsetDateTime.parse(createdRaw, JIRA_DATE_FORMAT).toInstant()
                    : Instant.EPOCH;

            Set<String> affectedVersions = new LinkedHashSet<>();
            JsonNode versionNodes = issue.path("fields").path("versions");
            if (versionNodes.isArray()) {
                versionNodes.forEach(version -> {
                    String name = version.path("name").asText(null);
                    if (name != null && !name.isBlank()) {
                        affectedVersions.add(JiraProjectSession.normalize(name));
                    }
                });
            }

            tickets.add(new JiraBugTicket(key, createdDate, List.copyOf(affectedVersions)));
        }
    }
}
