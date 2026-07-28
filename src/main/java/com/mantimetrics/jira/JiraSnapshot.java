package com.mantimetrics.jira;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/**
 * Immutable snapshot of a resolved Jira ticket. Carries the fields needed by the milestone-1
 * labeling flow and by the ticket-level (TLP) features.
 *
 * @param key Jira issue key
 * @param createdDate timestamp when the ticket was created
 * @param affectedVersions normalized affected versions declared in Jira
 * @param priorityRank ordinal priority rank (1=Trivial ... 5=Blocker; 0=unknown)
 * @param typeRisk empirical risk rank of the issue type (0=unknown)
 * @param componentCount number of Jira components attached to the ticket
 * @param resolvedDate resolution timestamp, or {@code null} when unresolved/unknown
 */
public record JiraSnapshot(
        String key,
        Instant createdDate,
        List<String> affectedVersions,
        int priorityRank,
        int typeRisk,
        int componentCount,
        Instant resolvedDate
) {
    /**
     * Normalizes the payload into a null-safe immutable representation.
     */
    @SuppressWarnings("DataFlowIssue")
    public JiraSnapshot {
        key = Objects.requireNonNull(key, "key");
        createdDate = Objects.requireNonNull(createdDate, "createdDate");
        affectedVersions = List.copyOf(new LinkedHashSet<>(Objects.requireNonNull(affectedVersions, "affectedVersions")));
    }

    /**
     * Convenience constructor for the labeling flow, which only needs key, creation date and
     * affected versions. TLP fields default to zero / {@code null}.
     *
     * @param key Jira issue key
     * @param createdDate ticket creation timestamp
     * @param affectedVersions normalized affected versions
     */
    public JiraSnapshot(String key, Instant createdDate, List<String> affectedVersions) {
        this(key, createdDate, affectedVersions, 0, 0, 0, null);
    }

    /**
     * Reports whether the ticket contains at least one affected version.
     *
     * @return {@code true} when affected versions are present
     */
    public boolean hasAffectedVersions() {
        return !affectedVersions.isEmpty();
    }
}
