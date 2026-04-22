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
 * the Proportion algorithm (Predicted_IV = FV - (FV - OV) * P) when JIRA metadata is incomplete.
 * P is calibrated from tickets that do have affected versions; defaults to 1.0 when no training
 * data is available (equivalent to IV = OV).
 */
public final class HistoricalBugLabelIndexBuilder {

    /**
     * Builds the historical bug-label index from the full release history and resolved Jira tickets.
     *
     * @param timeline complete release timeline shared by Git and Jira
     * @param datasetTags releases selected for dataset generation
     * @param resolvedTickets resolved bug tickets fetched from Jira
     * @param releaseHistory preloaded release snapshots containing commit-range metadata
     * @return historical bug-label index plus audit summary
     */
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

        double proportionP = computeProportionP(ticketsByKey, fixReleaseByTicket, timeline);

        Map<String, Set<String>> buggyPathsByRelease = new HashMap<>();
        int withAffectedVersions = 0;
        int withProportionFallback = 0;

        for (Map.Entry<String, Integer> entry : fixReleaseByTicket.entrySet()) {
            JiraBugTicket ticket = ticketsByKey.get(entry.getKey());
            if (ticket == null) {
                continue;
            }

            int fixIndex = entry.getValue();
            boolean hadAffectedVersions = ticket.hasAffectedVersions();
            int injectedIndex = resolveInjectedVersionIndex(ticket, timeline, fixIndex, proportionP);

            if (hadAffectedVersions && injectedIndex < fixIndex) {
                withAffectedVersions++;
            } else if (!hadAffectedVersions && injectedIndex < fixIndex) {
                withProportionFallback++;
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
                        "proportion-fallback",
                        resolvedTickets.size(),
                        fixReleaseByTicket.size(),
                        withAffectedVersions,
                        withProportionFallback,
                        timeline.size(),
                        datasetTags.size(),
                        "The oracle uses affected versions when available. When absent, the injected version "
                                + "is predicted via the Proportion algorithm (mean P="
                                + String.format("%.4f", proportionP)
                                + "). When no calibration data exists, P defaults to 1.0 (IV=OV)."
                )
        );
    }

    /**
     * Indexes bug tickets by key for quick lookups during labeling.
     *
     * @param tickets resolved bug tickets
     * @return ticket index keyed by Jira issue key
     */
    private Map<String, JiraBugTicket> indexTickets(List<JiraBugTicket> tickets) {
        Map<String, JiraBugTicket> index = new HashMap<>();
        for (JiraBugTicket ticket : tickets) {
            index.put(ticket.key(), ticket);
        }
        return index;
    }

    /**
     * Scans the complete release history to identify the earliest fixing release and touched paths for each ticket.
     *
     * @param releaseHistory preloaded release snapshots
     * @param timeline complete release timeline
     * @param knownBugKeys Jira issue keys that represent resolved bug tickets
     * @param fixReleaseByTicket output map receiving the earliest fixing release index per ticket
     * @param touchedPathsByTicket output map receiving the paths touched by each ticket
     */
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

    /**
     * Computes the mean proportion P = mean((FV - IV) / (FV - OV)) calibrated from tickets
     * that have known affected versions. Returns 1.0 when no training data is available,
     * which causes the Proportion formula to predict IV = OV (conservative fallback).
     *
     * @param ticketsByKey ticket index
     * @param fixReleaseByTicket earliest fixing release index per ticket
     * @param timeline complete release timeline with optional tag dates
     * @return calibrated proportion P in [0, 1]
     */
    private double computeProportionP(
            Map<String, JiraBugTicket> ticketsByKey,
            Map<String, Integer> fixReleaseByTicket,
            ReleaseTimeline timeline
    ) {
        double sum = 0.0;
        int count = 0;

        for (Map.Entry<String, Integer> entry : fixReleaseByTicket.entrySet()) {
            JiraBugTicket ticket = ticketsByKey.get(entry.getKey());
            if (ticket == null || !ticket.hasAffectedVersions()) {
                continue;
            }

            int fv = entry.getValue();
            int ov = timeline.findOpeningVersionIndex(ticket.createdDate());

            List<Integer> candidates = new ArrayList<>();
            for (String av : ticket.affectedVersions()) {
                OptionalInt idx = timeline.findIndex(av);
                if (idx.isPresent() && idx.getAsInt() < fv) {
                    candidates.add(idx.getAsInt());
                }
            }
            if (candidates.isEmpty()) {
                continue;
            }
            int iv = candidates.stream().min(Integer::compareTo).orElseThrow();

            int denominator = fv - ov;
            if (denominator <= 0) {
                continue;
            }

            double p = (double) (fv - iv) / denominator;
            p = Math.min(1.0, Math.max(0.0, p));
            sum += p;
            count++;
        }

        return count == 0 ? 1.0 : sum / count;
    }

    /**
     * Resolves the injected version index for a ticket.
     * Uses JIRA affected versions when they are consistent, otherwise applies the Proportion algorithm.
     *
     * @param ticket resolved bug ticket being labeled
     * @param timeline complete release timeline
     * @param fixIndex release index where the fix was observed
     * @param proportionP calibrated proportion P used when affected versions are absent
     * @return injected release index; returns {@code fixIndex} when no valid range can be determined
     */
    private int resolveInjectedVersionIndex(
            JiraBugTicket ticket,
            ReleaseTimeline timeline,
            int fixIndex,
            double proportionP
    ) {
        // Preferred path: use JIRA affected versions
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

        // Proportion fallback: Predicted_IV = FV - (FV - OV) * P
        int ov = timeline.findOpeningVersionIndex(ticket.createdDate());
        if (ov >= fixIndex) {
            return fixIndex;  // no valid range; ticket opened after its fix release
        }
        int predicted = (int) Math.round(fixIndex - (fixIndex - ov) * proportionP);
        return Math.max(ov, Math.min(predicted, fixIndex - 1));
    }

    /**
     * Copies a map of mutable sets into an immutable equivalent.
     *
     * @param source source map containing mutable set values
     * @return immutable map with immutable set values
     */
    private Map<String, Set<String>> immutableSetValues(Map<String, Set<String>> source) {
        Map<String, Set<String>> copy = new HashMap<>();
        source.forEach((key, value) -> copy.put(key, Set.copyOf(value)));
        return copy;
    }
}
