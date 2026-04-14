package com.mantimetrics.analysis;

import com.mantimetrics.jira.JiraBugTicket;
import com.mantimetrics.labeling.ReleaseTimeline;

import java.util.List;

record ProjectReleasePlan(
        String owner,
        String repo,
        ReleaseTimeline timeline,
        List<String> selectedTags,
        List<JiraBugTicket> resolvedTickets
) {
}
