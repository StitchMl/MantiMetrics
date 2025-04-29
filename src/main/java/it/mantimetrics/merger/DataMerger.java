package it.mantimetrics.merger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DataMerger {

    /**
     * Merges static metrics records with commit records.
     * Applies labeling logic:
     * - If ticketType is "defect", ticketStatus is "Closed" or "Resolved" and resolution is "Fixed", the method is buggy.
     *
     * @param staticMetrics List of records extracted from static metrics
     * @param commitMetrics List of records extracted from commits
     * @param config Configuration (any other logics)
     * @return List of merged records
     */
    public static List<Map<String, Object>> merge(
            List<Map<String, Object>> staticMetrics,
            List<Map<String, Object>> commitMetrics,
            Map<String, String> config) {

        List<Map<String, Object>> mergedRecords = new ArrayList<>();

        // Use "methodName" and "releaseId" as keys for the union
        for (Map<String, Object> staticRecord : staticMetrics) {
            String staticMethod = (String) staticRecord.get("methodName");
            String staticRelease = (String) staticRecord.get("releaseId");

            // Start with the static record
            Map<String, Object> mergedRecord = new HashMap<>(staticRecord);

            // Integrates data from commits that match
            for (Map<String, Object> commitRecord : commitMetrics) {
                String commitMethod = (String) commitRecord.get("methodName");
                String commitRelease = (String) commitRecord.get("releaseId");

                if (staticMethod.equals(commitMethod) && staticRelease.equals(commitRelease)) {
                    mergedRecord.putAll(commitRecord);
                }
            }

            // Label buggy: checks whether the commit has indicated a bug according to the criteria
            String ticketType = (String) mergedRecord.get("ticketType");
            String ticketStatus = (String) mergedRecord.get("ticketStatus");
            String resolution = (String) mergedRecord.get("resolution");

            if ("defect".equalsIgnoreCase(ticketType) &&
                    ("Closed".equalsIgnoreCase(ticketStatus) || "Resolved".equalsIgnoreCase(ticketStatus)) &&
                    "Fixed".equalsIgnoreCase(resolution)) {
                mergedRecord.put("bugLabel", "bug");
            } else {
                mergedRecord.put("bugLabel", "non bug");
            }

            mergedRecords.add(mergedRecord);
        }
        return mergedRecords;
    }
}