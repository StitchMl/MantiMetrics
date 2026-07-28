package com.mantimetrics.datasetSetting;

import java.util.Locale;
import java.util.StringJoiner;

/**
 * Serializes class-level dataset rows into CSV lines matching the exported header.
 */
final class DatasetRowToCSV {

    private DatasetRowToCSV() {
        throw new AssertionError("Do not instantiate DatasetRowToCSV");
    }

    /**
     * Formats the shared payload plus the class name into one CSV line.
     *
     * @param data shared dataset payload
     * @param entityName class name to serialize
     * @return CSV line matching the exported header
     */
    static String format(DatasetRowData data, String entityName) {
        int loc = data.metrics().getLoc();
        double density = data.nSmells() / (double) Math.max(loc, 1);
        StringJoiner joiner = new StringJoiner(",");
        joiner.add(data.projectName())
                .add(data.path())
                .add(quote(entityName))
                .add(data.releaseId())
                .add(String.valueOf(loc))
                .add(String.valueOf(data.metrics().getWmc()))
                .add(String.valueOf(data.metrics().getLcom()))
                .add(String.valueOf(data.nSmells()))
                .add(String.format(Locale.ROOT, "%.4f", density))
                .add(String.valueOf(data.touches()))
                .add(String.valueOf(data.issueTouches()))
                .add(String.valueOf(data.authors()))
                .add(String.valueOf(data.addedLines()))
                .add(String.valueOf(data.deletedLines()))
                .add(String.valueOf(data.churn()))
                .add(String.valueOf(data.totalTouches()))
                .add(String.valueOf(data.totalIssueTouches()))
                .add(String.valueOf(data.totalAuthors()))
                .add(String.valueOf(data.totalChurn()))
                .add(String.valueOf(data.ageInReleases()))
                .add(String.valueOf(data.maxLoc()))
                .add(String.valueOf(data.maxWmc()))
                .add(String.valueOf(data.maxNSmells()))
                .add(String.valueOf(data.priorityMax()))
                .add(String.valueOf(data.priorityAvg()))
                .add(String.valueOf(data.typeRiskMax()))
                .add(String.valueOf(data.typeRiskAvg()))
                .add(String.valueOf(data.componentCountMax()))
                .add(String.valueOf(data.componentCountAvg()))
                .add(String.valueOf(data.openTickets()))
                .add(String.valueOf(data.tlccLin()))
                .add(String.valueOf(data.tlccLog()))
                .add(String.valueOf(data.prevCodeSmells()))
                .add(yesNo(data.prevBuggy()))
                .add(yesNo(data.buggy()));
        return joiner.toString();
    }

    private static String quote(String value) {
        return '"' + value.replace("\"", "\"\"") + '"';
    }

    private static String yesNo(boolean value) {
        return value ? "yes" : "no";
    }
}
