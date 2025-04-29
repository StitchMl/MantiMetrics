package it.mantimetrics.merger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DataMerger {

    /**
     * Merges static metrics records with those derived from commits,
     * based on the 'methodName' and 'releaseId' fields.
     * <p>
     * The method uses the following logic:
     * - For each static metrics record, a match is searched for in commitMetrics.
     * - If a match is found, the data is merged.
     * - Labelling logic is applied: if "ticketType" is "defect", "ticketStatus" is "Closed"
     * (or "Resolved") and "resolution" is "Fixed", then the method is labeled "bug"; otherwise "not bug".
     *
     * @param staticMetrics list of records extracted from the static metrics part
     * @param commitMetrics list of records extracted from the commit part
     * @param config map of configurations (possibly for further future logics)
     * @return list of merged records containing both metrics and the bug label
     */
    public static List<Map<String, Object>> merge(
            List<Map<String, Object>> staticMetrics,
            List<Map<String, Object>> commitMetrics,
            Map<String, String> config) {

        List<Map<String, Object>> mergedRecords = new ArrayList<>();

        for (Map<String, Object> staticRecord : staticMetrics) {
            String staticMethod = (String) staticRecord.get("methodName");
            String staticRelease = (String) staticRecord.get("releaseId");

            // Creates a basic copy of the static record into which the data will be merged
            Map<String, Object> mergedRecord = new HashMap<>(staticRecord);

            // Search the commit dataset, using the keys 'methodName' and 'releaseId'.
            for (Map<String, Object> commitRecord : commitMetrics) {
                String commitMethod = (String) commitRecord.get("methodName");
                String commitRelease = (String) commitRecord.get("releaseId");

                if (staticMethod.equals(commitMethod) && staticRelease.equals(commitRelease)) {
                    // Merging data: commit information overwrites any duplicates
                    mergedRecord.putAll(commitRecord);
                }
            }

            // Applies logic for bugginess labeling:
            // If ticketType is 'defect', ticketStatus is 'Closed' or 'Resolved' and resolution is 'Fixed'
            String ticketType = (String) mergedRecord.get("ticketType");
            String ticketStatus = (String) mergedRecord.get("ticketStatus");
            String resolution = (String) mergedRecord.get("resolution");

            if ("defect".equals(ticketType) &&
                    (("Closed".equals(ticketStatus)) || ("Resolved".equals(ticketStatus))) &&
                    "Fixed".equals(resolution)) {
                mergedRecord.put("bugLabel", "bug");
            } else {
                mergedRecord.put("bugLabel", "non bug");
            }

            mergedRecords.add(mergedRecord);
        }

        return mergedRecords;
    }
}