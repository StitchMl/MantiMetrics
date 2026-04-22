package com.mantimetrics.jira;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/**
 * Immutable snapshot of a resolved bug ticket as exposed by JIRA.
 * Only the information needed by the milestone-1 labeling flow is retained.
 *
 * @param key Jira issue key
 * @param createdDate timestamp when the ticket was created in Jira
 * @param affectedVersions normalized affected versions declared in Jira
 */
public record JiraBugTicket(
        String key,
        Instant createdDate,
        List<String> affectedVersions
) {
    /**
     * Normalizes the bug ticket payload into a null-safe immutable representation.
     *
     * @param key Jira issue key
     * @param createdDate timestamp when the ticket was created in Jira
     * @param affectedVersions normalized affected versions declared in Jira
     */
    @SuppressWarnings("DataFlowIssue")
    public JiraBugTicket {
        key = Objects.requireNonNull(key, "key");
        createdDate = Objects.requireNonNull(createdDate, "createdDate");
        affectedVersions = List.copyOf(new LinkedHashSet<>(Objects.requireNonNull(affectedVersions, "affectedVersions")));
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
