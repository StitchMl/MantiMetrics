package com.mantimetrics.gitissue;

import com.mantimetrics.jira.JiraSnapshot;

import java.util.List;

/**
 * Converts raw GitHub bug issues into the {@link JiraSnapshot} model used by the labeling flow.
 * GitHub issues have no structured affected-versions field, so the injected version is later
 * estimated via the Proportion technique. Keys are namespaced as {@code GH-<number>} to avoid
 * clashing with Jira keys.
 */
public final class GitIssueMapper {

    /**
     * Maps raw GitHub issues into bug-ticket snapshots.
     *
     * @param issues raw GitHub bug issues
     * @return bug-ticket snapshots with {@code GH-<number>} keys and empty affected versions
     */
    public List<JiraSnapshot> toBugTickets(List<GitIssueClient.RawIssue> issues) {
        return issues.stream()
                .map(issue -> new JiraSnapshot("GH-" + issue.number(), issue.createdAt(), List.of()))
                .toList();
    }
}
