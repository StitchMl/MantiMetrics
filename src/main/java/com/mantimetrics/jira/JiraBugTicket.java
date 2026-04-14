package com.mantimetrics.jira;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/**
 * Immutable snapshot of a resolved bug ticket as exposed by JIRA.
 * Only the information needed by the milestone-1 labeling flow is retained.
 */
public record JiraBugTicket(
        String key,
        List<String> affectedVersions
) {
    public JiraBugTicket {
        key = Objects.requireNonNull(key, "key");
        affectedVersions = List.copyOf(new LinkedHashSet<>(Objects.requireNonNull(affectedVersions, "affectedVersions")));
    }

    public boolean hasAffectedVersions() {
        return !affectedVersions.isEmpty();
    }
}
