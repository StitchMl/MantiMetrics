package it.mantimetrics.merger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class DataMerger {

    public static List<Map<String, Object>> merge(List<Map<String, Object>> staticMetrics,
                                                  List<Map<String, Object>> commitMetrics,
                                                  java.util.Map<String, String> config) {
        List<Map<String, Object>> mergedRecords = new ArrayList<>();

        // A simple example: merge based on 'methodName' and 'releaseId'.
        // You will have to implement robust logic to merge records if there are multiple changes for the same method
        for (Map<String, Object> s : staticMetrics) {
            for (Map<String, Object> c : commitMetrics) {
                if (s.get("methodName").equals(c.get("methodName"))
                        && s.get("releaseId").equals(c.get("releaseId"))) {
                    s.putAll(c);
                    // Apply the filter on 33% of releases
                    if (isWithinReleaseRange(s.get("releaseId").toString(), config.get("release.percentage"))) {
                        // Determines the bugginess label
                        String label = determineBugLabel(s);
                        s.put("bugLabel", label);
                        mergedRecords.add(s);
                    }
                }
            }
        }
        return mergedRecords;
    }

    private static boolean isWithinReleaseRange(String releaseId, String percentageStr) {
        // Implements the logic to consider only the first 33% of releases
        // You can manage it by dates, semantic versions or an ordered list
        return true; // placeholder
    }

    private static String determineBugLabel(Map<String, Object> record) {
        // If the record contains ticket information in line with the criteria:
        // ticketType = defect, ticketStatus in [Closed, Resolved] and resolution = Fixed, then label = "bug"
        if ("defect".equals(record.get("ticketType"))
                && "Closed".equals(record.get("ticketStatus"))
                && "Fixed".equals(record.get("resolution"))) {
            return "bug";
        }
        return "non bug";
    }
}
