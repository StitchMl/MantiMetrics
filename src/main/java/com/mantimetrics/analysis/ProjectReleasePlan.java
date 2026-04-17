package com.mantimetrics.analysis;

import com.mantimetrics.jira.JiraBugTicket;
import com.mantimetrics.labeling.ReleaseTimeline;

import java.util.List;

/**
 * Immutable release-planning result shared by the downstream project processor.
 *
 * @param owner repository owner
 * @param repo repository name
 * @param timeline full chronological release timeline common to Git and Jira
 * @param selectedTags prefix of releases selected for dataset generation
 * @param resolvedTickets resolved bug tickets fetched from Jira
 */
record ProjectReleasePlan(
        String owner,
        String repo,
        ReleaseTimeline timeline,
        List<String> selectedTags,
        List<JiraBugTicket> resolvedTickets
) {
}
