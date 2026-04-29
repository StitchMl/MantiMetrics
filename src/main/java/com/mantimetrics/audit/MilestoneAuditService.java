package com.mantimetrics.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.mantimetrics.dataset.DatasetColumns;
import com.mantimetrics.dataset.DatasetCsvTableReader;
import com.mantimetrics.dataset.DatasetTable;
import com.mantimetrics.labeling.HistoricalBugLabelIndex;
import org.jetbrains.annotations.NotNull;

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

    /**
     * Creates an audit writer that can inspect the generated raw CSV datasets.
     *
     * @param tableReader reader used to load CSV datasets into tabular form
     */
    public MilestoneAuditService(DatasetCsvTableReader tableReader) {
        this.tableReader = tableReader;
    }

    /**
     * Writes the milestone-1 audit JSON next to the dataset-derived artifacts.
     *
     * @param rawCsvPath raw dataset CSV path
     * @param timelineReleaseCount number of releases in the full historical timeline
     * @param selectedReleaseCount number of releases kept for dataset generation
     * @param labelingSummary summary of the historical labeling strategy
     * @param linkageRate proportion of commits linked to a Jira ticket (issueLinked / total)
     * @throws IOException when the audit file cannot be written
     */
    public void write(
            Path rawCsvPath,
            int timelineReleaseCount,
            int selectedReleaseCount,
            HistoricalBugLabelIndex.Summary labelingSummary,
            double linkageRate
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
        audit.put("linkageRate", String.format(java.util.Locale.ROOT, "%.4f", linkageRate));
        audit.put("linkageRateNote",
                "Proportion of commits touching Java files that carry at least one Jira issue key. "
                        + "Values below 0.20 indicate poor traceability and reduced label reliability.");

        Map<String, Object> snoring = new LinkedHashMap<>();
        snoring.put("timelineReleaseCount", timelineReleaseCount);
        snoring.put("selectedReleaseCount", selectedReleaseCount);
        snoring.put("selectedPercentageOfTimeline",
                timelineReleaseCount == 0 ? 0.0 : (selectedReleaseCount * 100.0) / timelineReleaseCount);
        snoring.put("policy", "Rows are emitted only for the oldest release window; the full timeline is still used for labels.");
        audit.put("snoring", snoring);

        Map<String, Object> labeling = getStringObjectMap(labelingSummary);
        audit.put("labeling", labeling);

        audit.put("kappaNote",
                "Cohen's Kappa is a classifier-evaluation metric and is not computed during Milestone 1 dataset extraction.");

        Path outputPath = resolveAuditPath(rawCsvPath);
        JSON.writeValue(outputPath.toFile(), audit);
    }

    /**
     * Converts the labeling summary into a JSON-friendly ordered map.
     *
     * @param labelingSummary historical labeling summary
     * @return ordered map ready to be embedded in the audit document
     */
    @NotNull
    private static Map<String, Object> getStringObjectMap(HistoricalBugLabelIndex.Summary labelingSummary) {
        Map<String, Object> labeling = new LinkedHashMap<>();
        labeling.put("strategy", labelingSummary.strategy());
        labeling.put("totalResolvedTickets", labelingSummary.totalResolvedTickets());
        labeling.put("ticketsWithFixCommit", labelingSummary.ticketsWithFixCommit());
        labeling.put("ticketsUsingAffectedVersions", labelingSummary.ticketsUsingAffectedVersions());
        labeling.put("ticketsUsingTotalFallback", labelingSummary.ticketsUsingTotalFallback());
        labeling.put("notes", labelingSummary.notes());
        return labeling;
    }

    /**
     * Resolves the audit file location associated with a raw dataset CSV.
     *
     * @param rawCsvPath raw dataset CSV path
     * @return output path for the milestone audit JSON
     */
    private Path resolveAuditPath(Path rawCsvPath) {
        String fileName = rawCsvPath.getFileName().toString();
        String baseName = fileName.endsWith(".csv") ? fileName.substring(0, fileName.length() - 4) : fileName;
        return rawCsvPath.getParent().resolve(baseName + "_artifacts").resolve("milestone1-audit.json");
    }

    /**
     * Detects which entity column is present in the dataset.
     *
     * @param table loaded dataset table
     * @return {@code Class}, {@code Method} or {@code Unknown}
     */
    private String entityColumn(DatasetTable table) {
        if (table.header().contains("Class")) {
            return "Class";
        }
        if (table.header().contains("Method")) {
            return "Method";
        }
        return "Unknown";
    }

    /**
     * Counts the feature columns excluding identifiers and the target label.
     *
     * @param table loaded dataset table
     * @return number of feature columns
     */
    private int featureCount(DatasetTable table) {
        return (int) table.header().stream()
                .filter(column -> !DatasetColumns.identifierColumns().contains(column))
                .filter(column -> !DatasetColumns.BUGGY.equals(column))
                .count();
    }

    /**
     * Counts the distinct non-blank values for a dataset column.
     *
     * @param table loaded dataset table
     * @param column column to inspect
     * @return number of distinct non-blank values
     */
    @SuppressWarnings("SameParameterValue")
    private long distinctCount(DatasetTable table, String column) {
        return table.rows().stream()
                .map(row -> row.getOrDefault(column, ""))
                .filter(value -> !value.isBlank())
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new))
                .size();
    }

    /**
     * Counts the rows whose column value matches the expected text ignoring case.
     *
     * @param table loaded dataset table
     * @param column column to inspect
     * @param expected expected text value
     * @return number of matching rows
     */
    @SuppressWarnings("SameParameterValue")
    private long countEquals(DatasetTable table, String column, String expected) {
        return table.rows().stream()
                .filter(row -> expected.equalsIgnoreCase(row.getOrDefault(column, "")))
                .count();
    }

    /**
     * Counts the rows whose numeric column value is greater than zero.
     *
     * @param table loaded dataset table
     * @param column column to inspect
     * @return number of rows whose parsed value is positive
     */
    @SuppressWarnings("SameParameterValue")
    private long countGreaterThanZero(DatasetTable table, String column) {
        return table.rows().stream()
                .filter(row -> parseInt(row.get(column)) > 0)
                .count();
    }

    /**
     * Parses an integer value treating blank values as zero.
     *
     * @param value textual numeric value
     * @return parsed integer, or {@code 0} when the value is blank
     */
    private int parseInt(String value) {
        return value == null || value.isBlank() ? 0 : Integer.parseInt(value.trim());
    }
}
