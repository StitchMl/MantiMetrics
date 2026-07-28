package com.mantimetrics.orchestrator;

import com.mantimetrics.datasetOutput.CSVWriter;
import com.mantimetrics.history.StoreReleaseInMemory;
import com.mantimetrics.labeling.ReleaseLabeling;
import com.mantimetrics.datasetSetting.DatasetRow;
import com.mantimetrics.jira.JiraSnapshot;

import java.io.BufferedWriter;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Mutable execution context associated with one dataset granularity while a project is being processed.
 *
 * @param owner repository owner
 * @param repo repository name
 * @param csvOut CSV writer service used to append rows
 * @param prevData rows produced for the previous release, keyed by dataset identifier
 * @param historyStore cumulative history state shared across releases for this granularity
 * @param labelIndex historical bug labels available for the project timeline
 * @param writer buffered writer bound to the output CSV file
 * @param sonarSmellsByTag SonarCloud file-smell counts keyed by release tag; empty map when unavailable
 * @param excludeChurnZero whether to drop rows whose current-release churn is zero
 * @param ticketsByKey all resolved tickets keyed by issue key (TLP)
 * @param openTicketsByRelease number of open tickets at each release snapshot (TLP)
 * @param ticketTouchedPaths issue key -> touched paths (TLCC)
 * @param orderedTicketsByRelease chronological ticket keys up to each release (TLCC window)
 */
public record SharedStatus(
        String owner,
        String repo,
        CSVWriter csvOut,
        Map<String, DatasetRow> prevData,
        StoreReleaseInMemory historyStore,
        ReleaseLabeling labelIndex,
        BufferedWriter writer,
        Map<String, Map<String, Integer>> sonarSmellsByTag,
        boolean excludeChurnZero,
        Map<String, JiraSnapshot> ticketsByKey,
        Map<String, Integer> openTicketsByRelease,
        Map<String, Set<String>> ticketTouchedPaths,
        Map<String, List<String>> orderedTicketsByRelease
) {
}
