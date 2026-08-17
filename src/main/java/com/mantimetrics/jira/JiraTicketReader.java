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
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reads Jira project data such as bug keys, versions and resolved bug tickets.
 */
final class JiraTicketReader {
    private static final Logger LOG = LoggerFactory.getLogger(JiraTicketReader.class);
    private static final int PAGE_SIZE = 100;
    private static final String PARAM_FIELDS = "fields";
    private static final String S1 = "startAt";
    private static final String S2 = "maxResults";
    private static final String S3 = "issues";
    private static final String S4 = "total";

    private final JiraClient jsonClient;

    /**
     * Creates a project reader backed by the shared Jira JSON client.
     *
     * @param jsonClient Jira JSON client used for REST calls
     */
    JiraTicketReader(JiraClient jsonClient) {
        this.jsonClient = jsonClient;
    }

    /**
     * Fetches all bug issue keys matching the configured Jira search.
     *
     * @param session initialized Jira project session
     * @return bug keys returned by Jira
     * @throws JiraClientException when Jira cannot be queried
     */
    List<String> fetchBugKeys(JiraProjectState session) throws JiraClientException {
        Set<String> keys = new HashSet<>();
        int startAt = 0;

        try {
            while (true) {
                URI uri = new URIBuilder(session.searchBase())
                        .addParameter(S1, String.valueOf(startAt))
                        .addParameter(S2, String.valueOf(PAGE_SIZE))
                        .build();
                JsonNode response = jsonClient.get(uri, session.authHeader());
                collectIssueKeys(response.path(S3), keys);

                int total = response.path(S4).asInt();
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
    List<String> fetchProjectVersions(JiraProjectState session, String projectKey) throws JiraClientException {
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
                    names.add(JiraProjectState.normalize(name));
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
     * Fetches a map of normalized version name -> JIRA release date for a project.
     * Only versions that carry a {@code releaseDate} field are included.
     *
     * @param session initialized Jira project session
     * @param projectKey Jira project key
     * @return map of normalized version name to release date instant
     * @throws JiraClientException when Jira cannot be queried
     */
    Map<String, Instant> fetchVersionDates(JiraProjectState session, String projectKey)
            throws JiraClientException {
        try {
            URI uri = new URIBuilder(session.baseUrl() + "/rest/api/2/project/" + projectKey + "/versions")
                    .build();
            JsonNode versions = jsonClient.get(uri, session.authHeader());
            Map<String, Instant> dates = new LinkedHashMap<>();
            versions.forEach(version -> {
                String name = version.path("name").asText(null);
                String dateStr = version.path("releaseDate").asText(null);
                if (name != null && !name.isBlank() && dateStr != null && !dateStr.isBlank()) {
                    try {
                        Instant date = LocalDate.parse(dateStr).atStartOfDay(ZoneOffset.UTC).toInstant();
                        dates.put(JiraProjectState.normalize(name), date);
                    } catch (Exception parseException) {
                        LOG.warn("Unparseable JIRA releaseDate '{}' for version '{}'", dateStr, name);
                    }
                }
            });
            LOG.debug("JIRA project {} - {} version dates fetched", projectKey, dates.size());
            return dates;
        } catch (IOException exception) {
            throw new JiraClientException("I/O error fetching version dates", exception);
        } catch (Exception exception) {
            throw new JiraClientException("fetchVersionDates error", exception);
        }
    }

    /**
     * Fetches the resolved bug tickets used by the historical labeling flow.
     *
     * @param session initialized Jira project session
     * @return resolved Jira bug tickets
     * @throws JiraClientException when Jira cannot be queried
     */
    List<JiraSnapshot> fetchResolvedBugTickets(JiraProjectState session) throws JiraClientException {
        List<JiraSnapshot> tickets = new ArrayList<>();
        int startAt = 0;

        try {
            while (true) {
                URI uri = new URIBuilder(session.searchBase())
                        .addParameter(PARAM_FIELDS, "key,versions,created")
                        .addParameter(S1, String.valueOf(startAt))
                        .addParameter(S2, String.valueOf(PAGE_SIZE))
                        .build();
                JsonNode response = jsonClient.get(uri, session.authHeader());
                collectTickets(response.path(S3), tickets);

                int total = response.path(S4).asInt();
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
    private void collectTickets(JsonNode issues, List<JiraSnapshot> tickets) {
        for (JsonNode issue : issues) {
            String key = issue.path("key").asText(null);
            if (key == null || key.isBlank()) {
                continue;
            }

            String createdRaw = issue.path(PARAM_FIELDS).path("created").asText(null);
            Instant createdDate = (createdRaw != null && !createdRaw.isBlank())
                    ? OffsetDateTime.parse(createdRaw, JIRA_DATE_FORMAT).toInstant()
                    : Instant.EPOCH;

            Set<String> affectedVersions = new LinkedHashSet<>();
            JsonNode versionNodes = issue.path(PARAM_FIELDS).path("versions");
            if (versionNodes.isArray()) {
                versionNodes.forEach(version -> {
                    String name = version.path("name").asText(null);
                    if (name != null && !name.isBlank()) {
                        affectedVersions.add(JiraProjectState.normalize(name));
                    }
                });
            }

            tickets.add(new JiraSnapshot(key, createdDate, List.copyOf(affectedVersions)));
        }
    }

    /**
     * Fetches all resolved tickets (any issue type) with the fields needed by the TLP features.
     *
     * @param session initialized Jira project session
     * @return resolved tickets of every type
     * @throws JiraClientException when Jira cannot be queried
     */
    List<JiraSnapshot> fetchAllResolvedTickets(JiraProjectState session) throws JiraClientException {
        List<JiraSnapshot> tickets = new ArrayList<>();
        int startAt = 0;
        try {
            while (true) {
                URI uri = new URIBuilder(session.allTicketsSearchBase())
                        .addParameter(PARAM_FIELDS, "key,versions,created,resolutiondate,priority,issuetype,components")
                        .addParameter(S1, String.valueOf(startAt))
                        .addParameter(S2, String.valueOf(PAGE_SIZE))
                        .build();
                JsonNode response = jsonClient.get(uri, session.authHeader());
                collectFullTickets(response.path(S3), tickets);

                int total = response.path(S4).asInt();
                startAt += PAGE_SIZE;
                if (startAt >= total) {
                    break;
                }
            }
        } catch (IOException exception) {
            throw new JiraClientException("I/O Error to JIRA (all tickets)", exception);
        } catch (Exception exception) {
            throw new JiraClientException("Error fetchAllResolvedTickets", exception);
        }
        return List.copyOf(tickets);
    }

    /**
     * Parses the extended ticket payload (priority, type, components, resolution date) for TLP.
     *
     * @param issues Jira issues array
     * @param tickets target list receiving the extracted tickets
     */
    private void collectFullTickets(JsonNode issues, List<JiraSnapshot> tickets) {
        for (JsonNode issue : issues) {
            String key = issue.path("key").asText(null);
            if (key == null || key.isBlank()) {
                continue;
            }
            JsonNode fields = issue.path(PARAM_FIELDS);

            Instant created = parseJiraDate(fields.path("created").asText(null));
            Instant resolved = parseJiraDate(fields.path("resolutiondate").asText(null));

            Set<String> affectedVersions = new LinkedHashSet<>();
            JsonNode versionNodes = fields.path("versions");
            if (versionNodes.isArray()) {
                versionNodes.forEach(version -> {
                    String name = version.path("name").asText(null);
                    if (name != null && !name.isBlank()) {
                        affectedVersions.add(JiraProjectState.normalize(name));
                    }
                });
            }

            int priorityRank = priorityRank(fields.path("priority").path("name").asText(""));
            int typeRisk = typeRisk(fields.path("issuetype").path("name").asText(""));
            JsonNode components = fields.path("components");
            int componentCount = components.isArray() ? components.size() : 0;

            tickets.add(new JiraSnapshot(
                    key,
                    created != null ? created : Instant.EPOCH,
                    List.copyOf(affectedVersions),
                    priorityRank,
                    typeRisk,
                    componentCount,
                    resolved));
        }
    }

    /**
     * Parses a Jira timestamp, returning {@code null} when absent or malformed.
     *
     * @param raw raw Jira date string
     * @return parsed instant, or {@code null}
     */
    private Instant parseJiraDate(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(raw, JIRA_DATE_FORMAT).toInstant();
        } catch (Exception exception) {
            return null;
        }
    }

    /**
     * Maps a Jira priority name to an ordinal rank (1=Trivial ... 5=Blocker; 0=unknown).
     *
     * @param name Jira priority name
     * @return ordinal priority rank
     */
    private static int priorityRank(String name) {
        return switch (name.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "blocker", "highest" -> 5;
            case "critical", "high" -> 4;
            case "major", "medium" -> 3;
            case "minor", "low" -> 2;
            case "trivial", "lowest" -> 1;
            default -> 0;
        };
    }

    /**
     * Maps a Jira issue type to an empirical risk rank (0=unknown). New features are treated as the
     * riskiest, tasks as the least risky.
     *
     * @param name Jira issue type name
     * @return empirical type-risk rank
     */
    private static int typeRisk(String name) {
        return switch (name.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "new feature" -> 4;
            case "improvement" -> 3;
            case "bug" -> 2;
            case "task", "sub-task", "subtask" -> 1;
            default -> 0;
        };
    }
}
