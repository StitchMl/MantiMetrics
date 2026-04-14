package com.mantimetrics.jira;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.http.client.utils.URIBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class JiraProjectReader {
    private static final Logger LOG = LoggerFactory.getLogger(JiraProjectReader.class);
    private static final int PAGE_SIZE = 100;

    private final JiraJsonClient jsonClient;

    JiraProjectReader(JiraJsonClient jsonClient) {
        this.jsonClient = jsonClient;
    }

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

    List<JiraBugTicket> fetchResolvedBugTickets(JiraProjectSession session) throws JiraClientException {
        List<JiraBugTicket> tickets = new ArrayList<>();
        int startAt = 0;

        try {
            while (true) {
                URI uri = new URIBuilder(session.searchBase())
                        .addParameter("fields", "key,versions")
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

    private void collectIssueKeys(JsonNode issues, Set<String> keys) {
        for (JsonNode issue : issues) {
            String key = issue.path("key").asText(null);
            if (key != null) {
                keys.add(key);
            }
        }
    }

    private void collectTickets(JsonNode issues, List<JiraBugTicket> tickets) {
        for (JsonNode issue : issues) {
            String key = issue.path("key").asText(null);
            if (key == null || key.isBlank()) {
                continue;
            }

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

            tickets.add(new JiraBugTicket(key, List.copyOf(affectedVersions)));
        }
    }
}
