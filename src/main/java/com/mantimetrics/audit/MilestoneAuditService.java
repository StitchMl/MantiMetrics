package com.mantimetrics.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.mantimetrics.dataset.DatasetColumns;
import com.mantimetrics.dataset.DatasetCsvTableReader;
import com.mantimetrics.dataset.DatasetTable;
import com.mantimetrics.labeling.HistoricalBugLabelIndex;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Writes a compact milestone-1 audit next to the derived artifacts so dataset readiness is explicit.
 */
public final class MilestoneAuditService {
    private static final ObjectMapper JSON = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    private final DatasetCsvTableReader tableReader;

    public MilestoneAuditService(DatasetCsvTableReader tableReader) {
        this.tableReader = tableReader;
    }

    public void write(
            Path rawCsvPath,
            int timelineReleaseCount,
            int selectedReleaseCount,
            HistoricalBugLabelIndex.Summary labelingSummary
    ) throws IOException {
        DatasetTable table = tableReader.read(rawCsvPath);
        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("rawDataset", rawCsvPath.toString());
        audit.put("rows", table.rowCount());
        audit.put("entityColumn", entityColumn(table));
        audit.put("featureCount", featureCount(table));
        audit.put("distinctReleasesInDataset", distinctCount(table, "ReleaseId"));
        audit.put("buggyRows", countEquals(table, DatasetColumns.BUGGY, "yes"));
        audit.put("cleanRows", countEquals(table, DatasetColumns.BUGGY, "no"));
        audit.put("smellyRows", countGreaterThanZero(table, DatasetColumns.NSMELLS));
        audit.put("requiredSmellColumnsPresent",
                table.header().contains("CodeSmells") && table.header().contains(DatasetColumns.NSMELLS));

        Map<String, Object> snoring = new LinkedHashMap<>();
        snoring.put("timelineReleaseCount", timelineReleaseCount);
        snoring.put("selectedReleaseCount", selectedReleaseCount);
        snoring.put("selectedPercentageOfTimeline",
                timelineReleaseCount == 0 ? 0.0 : (selectedReleaseCount * 100.0) / timelineReleaseCount);
        snoring.put("policy", "Rows are emitted only for the oldest release window; the full timeline is still used for labels.");
        audit.put("snoring", snoring);

        Map<String, Object> labeling = new LinkedHashMap<>();
        labeling.put("strategy", labelingSummary.strategy());
        labeling.put("totalResolvedTickets", labelingSummary.totalResolvedTickets());
        labeling.put("ticketsWithFixCommit", labelingSummary.ticketsWithFixCommit());
        labeling.put("ticketsUsingAffectedVersions", labelingSummary.ticketsUsingAffectedVersions());
        labeling.put("ticketsUsingTotalFallback", labelingSummary.ticketsUsingTotalFallback());
        labeling.put("notes", labelingSummary.notes());
        audit.put("labeling", labeling);

        audit.put("kappaNote",
                "Cohen's Kappa is a classifier-evaluation metric and is not computed during Milestone 1 dataset extraction.");

        Path outputPath = resolveAuditPath(rawCsvPath);
        JSON.writeValue(outputPath.toFile(), audit);
    }

    private Path resolveAuditPath(Path rawCsvPath) {
        String fileName = rawCsvPath.getFileName().toString();
        String baseName = fileName.endsWith(".csv") ? fileName.substring(0, fileName.length() - 4) : fileName;
        return rawCsvPath.getParent().resolve(baseName + "_artifacts").resolve("milestone1-audit.json");
    }

    private String entityColumn(DatasetTable table) {
        if (table.header().contains("Class")) {
            return "Class";
        }
        if (table.header().contains("Method")) {
            return "Method";
        }
        return "Unknown";
    }

    private int featureCount(DatasetTable table) {
        return (int) table.header().stream()
                .filter(column -> !DatasetColumns.identifierColumns().contains(column))
                .filter(column -> !DatasetColumns.BUGGY.equals(column))
                .count();
    }

    @SuppressWarnings("SameParameterValue")
    private long distinctCount(DatasetTable table, String column) {
        return table.rows().stream()
                .map(row -> row.getOrDefault(column, ""))
                .filter(value -> !value.isBlank())
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new))
                .size();
    }

    @SuppressWarnings("SameParameterValue")
    private long countEquals(DatasetTable table, String column, String expected) {
        return table.rows().stream()
                .filter(row -> expected.equalsIgnoreCase(row.getOrDefault(column, "")))
                .count();
    }

    @SuppressWarnings("SameParameterValue")
    private long countGreaterThanZero(DatasetTable table, String column) {
        return table.rows().stream()
                .filter(row -> parseInt(row.get(column)) > 0)
                .count();
    }

    private int parseInt(String value) {
        return value == null || value.isBlank() ? 0 : Integer.parseInt(value.trim());
    }
}
