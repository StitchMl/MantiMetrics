package com.mantimetrics.orchestrator;

import com.mantimetrics.history.ComulationMetricsCalculator;
import com.mantimetrics.feature.ClassMetrics;
import com.mantimetrics.datasetsetting.DatasetClassData;
import com.mantimetrics.datasetsetting.DatasetRow;
import com.mantimetrics.jira.JiraSnapshot;
import com.mantimetrics.utility.PathUtility;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

/**
 * Applies release-local Git history, cumulative history and historical bug labels to class rows.
 */
final class DatasetRowEnricher {

    /**
     * Enriches class-level rows with release-local and cumulative history features.
     *
     * @param rows parsed class rows for the current release
     * @param request immutable release request carrying history and labels
     * @return enriched class rows ready for serialization
     */
    List<DatasetClassData> enrichClasses(
            List<DatasetClassData> rows,
            ReleaseToDatasetRequest request
    ) {
        List<DatasetClassData> result = new ArrayList<>();
        for (DatasetClassData row : rows) {
            String relativePath = normalizedPath(row);
            List<String> commits = request.commitData().touchesFor(relativePath);
            int currentCodeSmells = codeSmellsForRow(row, request.sonarSmellsByFile());
            TlpAggregate tlp = aggregateTlp(request.commitData().issueKeysFor(relativePath), request.ticketsByKey());
            double[] tlcc = computeTlcc(relativePath, request.orderedTicketKeys(), request.ticketTouchedPaths());
            ComulationMetricsCalculator historyState = updateHistory(
                    row.getUniqueKey(), relativePath, request, row.getMetrics(), currentCodeSmells);
            DatasetClassData previous =
                    request.previousRows().get(row.getUniqueKey()) instanceof DatasetClassData type ? type : null;
            result.add(row.toBuilder()
                    .commitHashes(commits)
                    .codeSmells(currentCodeSmells)
                    .issueTouches(request.commitData().issueTouchesFor(relativePath).size())
                    .totalIssueTouches(historyState.totalIssueTouches())
                    .touches(commits.size())
                    .totalTouches(historyState.totalTouches())
                    .authors(distinctCount(request.commitData().authorsFor(relativePath)))
                    .totalAuthors(historyState.totalAuthors())
                    .addedLines(request.commitData().additionsFor(relativePath))
                    .deletedLines(request.commitData().deletionsFor(relativePath))
                    .churn(request.commitData().churnFor(relativePath))
                    .totalChurn(historyState.totalChurn())
                    .prevCodeSmells(previous != null ? previous.getCodeSmells() : 0)
                    .ageInReleases(historyState.ageInReleases())
                    .buggy(isBuggyRow(request.tag(), relativePath, request))
                    .prevBuggy(previous != null && previous.isBuggy())
                    .maxLoc(historyState.maxLoc())
                    .maxWmc(historyState.maxWmc())
                    .maxNSmells(historyState.maxNSmells())
                    .priorityMax(tlp.priorityMax())
                    .priorityAvg(tlp.priorityAvg())
                    .typeRiskMax(tlp.typeRiskMax())
                    .typeRiskAvg(tlp.typeRiskAvg())
                    .componentCountMax(tlp.componentCountMax())
                    .componentCountAvg(tlp.componentCountAvg())
                    .openTickets(request.openTickets())
                    .tlccLin(tlcc[0])
                    .tlccLog(tlcc[1])
                    .build());
        }
        return result;
    }

    /**
     * Updates the cumulative history state for a class row and returns the refreshed value.
     *
     * @param uniqueKey stable dataset identifier for the row
     * @param relativePath normalized relative source path
     * @param request immutable release request carrying commit history and the mutable store
     * @param metrics current-release class metrics
     * @param currentCodeSmells NSmells count for the current release
     * @return updated history state after processing the current release
     */
    private ComulationMetricsCalculator updateHistory(
            String uniqueKey,
            String relativePath,
            ReleaseToDatasetRequest request,
            ClassMetrics metrics,
            int currentCodeSmells
    ) {
        ComulationMetricsCalculator previous = request.historyStore().get(uniqueKey);
        List<String> totalAuthors = mergeAuthors(previous, distinctAuthors(request.commitData().authorsFor(relativePath)));
        ComulationMetricsCalculator updated = new ComulationMetricsCalculator(
                prevInt(previous, ComulationMetricsCalculator::totalTouches) + request.commitData().touchesFor(relativePath).size(),
                prevInt(previous, ComulationMetricsCalculator::totalIssueTouches) + request.commitData().issueTouchesFor(relativePath).size(),
                prevInt(previous, ComulationMetricsCalculator::totalChurn) + request.commitData().churnFor(relativePath),
                totalAuthors,
                prevInt(previous, ComulationMetricsCalculator::ageInReleases) + 1,
                Math.max(prevInt(previous, ComulationMetricsCalculator::maxLoc), metrics.getLoc()),
                Math.max(prevInt(previous, ComulationMetricsCalculator::maxWmc), metrics.getWmc()),
                Math.max(prevInt(previous, ComulationMetricsCalculator::maxNSmells), currentCodeSmells)
        );
        request.historyStore().put(uniqueKey, updated);
        return updated;
    }

    /** Merges the previous author list with the current release authors, preserving order. */
    private List<String> mergeAuthors(ComulationMetricsCalculator previous, List<String> currentAuthors) {
        List<String> totalAuthors = new ArrayList<>();
        if (previous != null) {
            totalAuthors.addAll(previous.authors());
        }
        currentAuthors.stream()
                .filter(author -> !totalAuthors.contains(author))
                .forEach(totalAuthors::add);
        return totalAuthors;
    }

    /** Returns {@code fn(prev)} when {@code prev} is non-null, otherwise {@code 0}. */
    private static int prevInt(ComulationMetricsCalculator prev, ToIntFunction<ComulationMetricsCalculator> fn) {
        return prev != null ? fn.applyAsInt(prev) : 0;
    }

    /**
     * Returns the SonarCloud code-smell (NSmells) count for a row, or 0 when absent from the index.
     *
     * @param row dataset row being enriched
     * @param sonarSmells SonarCloud file-level smell counts keyed by normalized path
     * @return NSmells count for the row
     */
    private int codeSmellsForRow(DatasetRow row, java.util.Map<String, Integer> sonarSmells) {
        return sonarSmells.getOrDefault(normalizedPath(row), 0);
    }

    /**
     * Reports whether the current row is historically labeled as buggy for the analyzed release.
     *
     * @param releaseId release identifier currently being processed
     * @param relativePath normalized relative source path
     * @param request immutable release request carrying the historical label index
     * @return {@code true} when the row belongs to a historically buggy file in the current release
     */
    private boolean isBuggyRow(String releaseId, String relativePath, ReleaseToDatasetRequest request) {
        return request.labelIndex().isBuggy(releaseId, relativePath);
    }

    /** Normalizes the row path into the canonical dataset representation. */
    private String normalizedPath(DatasetRow row) {
        return PathUtility.normalizeDatasetPath(row.getPath());
    }

    /** Removes duplicate authors while preserving encounter order. */
    private List<String> distinctAuthors(List<String> authors) {
        return authors.stream().distinct().toList();
    }

    /** Counts the distinct authors touching a row. */
    private int distinctCount(List<String> authors) {
        return distinctAuthors(authors).size();
    }

    /**
     * Aggregates the ticket-level (TLP) features (Priority, Type-risk, Component count) as Max and
     * Mean over the tickets linked to a class in the current release.
     *
     * @param ticketKeys issue keys linked to the class in the current release
     * @param ticketsByKey all resolved tickets keyed by issue key
     * @return aggregated TLP values (zeros when no ticket is linked)
     */
    private TlpAggregate aggregateTlp(List<String> ticketKeys, java.util.Map<String, JiraSnapshot> ticketsByKey) {
        List<Integer> priorities = new ArrayList<>();
        List<Integer> typeRisks = new ArrayList<>();
        List<Integer> componentCounts = new ArrayList<>();
        for (String key : ticketKeys) {
            JiraSnapshot ticket = ticketsByKey.get(key);
            if (ticket != null) {
                priorities.add(ticket.priorityRank());
                typeRisks.add(ticket.typeRisk());
                componentCounts.add(ticket.componentCount());
            }
        }
        return new TlpAggregate(
                maxOf(priorities), avgOf(priorities),
                maxOf(typeRisks), avgOf(typeRisks),
                maxOf(componentCounts), avgOf(componentCounts));
    }

    /** Returns the maximum of a list, or 0 when empty. */
    private static int maxOf(List<Integer> values) {
        return values.stream().mapToInt(Integer::intValue).max().orElse(0);
    }

    /** Returns the mean of a list, or 0.0 when empty. */
    private static double avgOf(List<Integer> values) {
        return values.isEmpty() ? 0.0 : values.stream().mapToInt(Integer::intValue).average().orElse(0.0);
    }

    /**
     * Computes the Temporal Locality (TLCC) of a class over the ticket window up to the current
     * release. {@code TLCC_Lin} weights the i-th ticket by 1/(1+N-i) (includes i=N, weight 1);
     * {@code TLCC_Log} weights by 1/ln(1+N-i) (excludes i=N to avoid ln(1)=0). Both are divided by N.
     *
     * @param path normalized class path
     * @param orderedTicketKeys chronological ticket keys up to the release (oldest first)
     * @param ticketTouchedPaths issue key -> touched paths
     * @return array {@code [tlccLin, tlccLog]}
     */
    private double[] computeTlcc(String path, List<String> orderedTicketKeys,
                                 java.util.Map<String, java.util.Set<String>> ticketTouchedPaths) {
        int n = orderedTicketKeys.size();
        if (n == 0) {
            return new double[]{0.0, 0.0};
        }
        double lin = 0.0;
        double log = 0.0;
        for (int idx = 0; idx < n; idx++) {
            int i = idx + 1;
            boolean touched = ticketTouchedPaths.getOrDefault(orderedTicketKeys.get(idx), java.util.Set.of()).contains(path);
            if (!touched) {
                continue;
            }
            lin += 1.0 / (1 + n - i);
            if (i < n) {
                log += 1.0 / Math.log(1 + (double)n - i);
            }
        }
        return new double[]{lin / n, log / n};
    }

    /** Immutable aggregated TLP values for one class-release row. */
    private record TlpAggregate(int priorityMax, double priorityAvg, int typeRiskMax, double typeRiskAvg,
                                int componentCountMax, double componentCountAvg) {
    }
}
