package com.mantimetrics.orchestrator;

import com.mantimetrics.jira.JiraSnapshot;
import com.mantimetrics.releaseSelection.ReleaseTimeline;

import java.util.List;

/**
 * Immutable release-planning result shared by the downstream project processor.
 *
 * @param owner repository owner
 * @param repo repository name
 * @param timeline full chronological release timeline common to Git and Jira
 * @param selectedTags prefix of releases selected for dataset generation
 * @param resolvedTickets resolved bug tickets fetched from Jira
 * @param allTickets all resolved tickets (any type) used for the ticket-level (TLP) features
 * @param ghTickets GitHub Issues (bug-labeled) fetched separately; unioned per-variant when requested
 */
public record ReleasePlan(
        String owner,
        String repo,
        ReleaseTimeline timeline,
        List<String> selectedTags,
        List<JiraSnapshot> resolvedTickets,
        List<JiraSnapshot> allTickets,
        List<JiraSnapshot> ghTickets
) {
}
