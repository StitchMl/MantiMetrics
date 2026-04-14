package com.mantimetrics.labeling;

import com.mantimetrics.analysis.ReleaseSnapshot;
import com.mantimetrics.jira.JiraBugTicket;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.Set;

/**
 * Builds the historical labeling oracle used by the milestone-1 dataset.
 * The implementation prefers affected versions when they are consistent and falls back to
 * the simplified "Total" strategy endorsed by the assignment when JIRA metadata is incomplete.
 */
public final class HistoricalBugLabelIndexBuilder {

    public HistoricalBugLabelIndex build(
            ReleaseTimeline timeline,
            List<String> datasetTags,
            List<JiraBugTicket> resolvedTickets,
            List<ReleaseSnapshot> releaseHistory
    ) {
        Objects.requireNonNull(timeline, "timeline");
        Objects.requireNonNull(datasetTags, "datasetTags");
        Objects.requireNonNull(resolvedTickets, "resolvedTickets");
        Objects.requireNonNull(releaseHistory, "releaseHistory");

        Map<String, JiraBugTicket> ticketsByKey = indexTickets(resolvedTickets);
        Map<String, Integer> fixReleaseByTicket = new HashMap<>();
        Map<String, Set<String>> touchedPathsByTicket = new HashMap<>();
        collectFixHistory(releaseHistory, timeline, ticketsByKey.keySet(), fixReleaseByTicket, touchedPathsByTicket);

        Map<String, Set<String>> buggyPathsByRelease = new HashMap<>();
        int withAffectedVersions = 0;
        int withTotalFallback = 0;

        for (Map.Entry<String, Integer> entry : fixReleaseByTicket.entrySet()) {
            JiraBugTicket ticket = ticketsByKey.get(entry.getKey());
            if (ticket == null) {
                continue;
            }

            int fixIndex = entry.getValue();
            int injectedIndex = resolveInjectedVersionIndex(ticket, timeline, fixIndex);
            if (ticket.hasAffectedVersions() && injectedIndex < fixIndex) {
                withAffectedVersions++;
            } else {
                withTotalFallback++;
            }

            if (injectedIndex >= fixIndex) {
                continue;
            }

            Set<String> touchedPaths = touchedPathsByTicket.getOrDefault(ticket.key(), Set.of());
            for (int releaseIndex = injectedIndex; releaseIndex < fixIndex; releaseIndex++) {
                String releaseTag = timeline.orderedTags().get(releaseIndex);
                buggyPathsByRelease.computeIfAbsent(releaseTag, ignored -> new LinkedHashSet<>()).addAll(touchedPaths);
            }
        }

        return new HistoricalBugLabelIndex(
                immutableSetValues(buggyPathsByRelease),
                new HistoricalBugLabelIndex.Summary(
                        "affected-versions-else-total",
                        resolvedTickets.size(),
                        fixReleaseByTicket.size(),
                        withAffectedVersions,
                        withTotalFallback,
                        timeline.size(),
                        datasetTags.size(),
                        "The oracle uses the full release history available today. When Jira affected versions are missing "
                                + "or inconsistent, the injected version falls back to the first release in the timeline "
                                + "to match the simplified Total policy allowed by the assignment."
                )
        );
    }

    private Map<String, JiraBugTicket> indexTickets(List<JiraBugTicket> tickets) {
        Map<String, JiraBugTicket> index = new HashMap<>();
        for (JiraBugTicket ticket : tickets) {
            index.put(ticket.key(), ticket);
        }
        return index;
    }

    private void collectFixHistory(
            List<ReleaseSnapshot> releaseHistory,
            ReleaseTimeline timeline,
            Set<String> knownBugKeys,
            Map<String, Integer> fixReleaseByTicket,
            Map<String, Set<String>> touchedPathsByTicket
    ) {
        for (ReleaseSnapshot snapshot : releaseHistory) {
            OptionalInt releaseIndex = timeline.findIndex(snapshot.tag());
            if (releaseIndex.isEmpty()) {
                continue;
            }
            int currentReleaseIndex = releaseIndex.getAsInt();

            snapshot.commitData().fileToIssueKeys().forEach((path, issueKeys) -> {
                for (String issueKey : issueKeys) {
                    if (!knownBugKeys.contains(issueKey)) {
                        continue;
                    }
                    fixReleaseByTicket.merge(issueKey, currentReleaseIndex, Math::min);
                    touchedPathsByTicket.computeIfAbsent(issueKey, ignored -> new LinkedHashSet<>()).add(path);
                }
            });
        }
    }

    private int resolveInjectedVersionIndex(JiraBugTicket ticket, ReleaseTimeline timeline, int fixIndex) {
        List<Integer> candidates = new ArrayList<>();
        for (String affectedVersion : ticket.affectedVersions()) {
            OptionalInt index = timeline.findIndex(affectedVersion);
            if (index.isPresent() && index.getAsInt() < fixIndex) {
                candidates.add(index.getAsInt());
            }
        }
        if (!candidates.isEmpty()) {
            return candidates.stream().min(Integer::compareTo).orElse(0);
        }
        return 0;
    }

    private Map<String, Set<String>> immutableSetValues(Map<String, Set<String>> source) {
        Map<String, Set<String>> copy = new HashMap<>();
        source.forEach((key, value) -> copy.put(key, Set.copyOf(value)));
        return copy;
    }
}
