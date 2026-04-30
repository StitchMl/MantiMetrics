package com.mantimetrics.model;

import java.util.StringJoiner;

/**
 * Serializes dataset rows into CSV lines matching the exported dataset header.
 */
final class DatasetCsvFormatter {

    /**
     * Prevents instantiation of the static utility class.
     */
    private DatasetCsvFormatter() {
        throw new AssertionError("Do not instantiate DatasetCsvFormatter");
    }

    /**
     * Formats the shared dataset payload plus the entity name into one CSV line.
     *
     * @param data shared dataset payload
     * @param entityName class or method name to serialize
     * @return CSV line matching the exported header
     */
    static String format(MetricDatasetRowData data, String entityName) {
        StringJoiner joiner = new StringJoiner(",");
        joiner.add(data.projectName())
                .add(data.path())
                .add(quote(entityName))
                .add(data.releaseId())
                .add(String.valueOf(data.metrics().getLoc()))
                .add(String.valueOf(data.metrics().getStmtCount()))
                .add(String.valueOf(data.metrics().getCyclomatic()))
                .add(String.valueOf(data.metrics().getCognitive()))
                .add(String.valueOf(data.metrics().getDistinctOperators()))
                .add(String.valueOf(data.metrics().getDistinctOperands()))
                .add(String.valueOf(data.metrics().getTotalOperators()))
                .add(String.valueOf(data.metrics().getTotalOperands()))
                .add(String.valueOf(data.metrics().getVocabulary()))
                .add(String.valueOf(data.metrics().getLength()))
                .add(String.valueOf(data.metrics().getVolume()))
                .add(String.valueOf(data.metrics().getDifficulty()))
                .add(String.valueOf(data.metrics().getEffort()))
                .add(String.valueOf(data.metrics().getMaxNestingDepth()))
                .add(binaryFlag(data.metrics().isLongMethod()))
                .add(binaryFlag(data.metrics().isGodClass()))
                .add(binaryFlag(data.metrics().isFeatureEnvy()))
                .add(binaryFlag(data.metrics().isDuplicatedCode()))
                .add(String.valueOf(data.codeSmells()))
                .add(String.valueOf(data.nSmells()))
                .add(String.format(java.util.Locale.ROOT, "%.4f", data.nSmells() / (double) Math.max(data.metrics().getLoc(), 1)))
                .add(String.valueOf(data.maxLoc()))
                .add(String.valueOf(data.maxCyclomatic()))
                .add(String.valueOf(data.maxCognitive()))
                .add(String.valueOf(data.maxNSmells()))
                .add(String.valueOf(data.maxStmtCount()))
                .add(String.valueOf(data.maxDistinctOperators()))
                .add(String.valueOf(data.maxDistinctOperands()))
                .add(String.valueOf(data.maxTotalOperators()))
                .add(String.valueOf(data.maxTotalOperands()))
                .add(String.valueOf(data.maxVocabulary()))
                .add(String.valueOf(data.maxLength()))
                .add(String.valueOf(data.maxVolume()))
                .add(String.valueOf(data.maxDifficulty()))
                .add(String.valueOf(data.maxEffort()))
                .add(String.valueOf(data.maxNestingDepth()))
                .add(binaryFlag(data.everLongMethod()))
                .add(binaryFlag(data.everGodClass()))
                .add(binaryFlag(data.everFeatureEnvy()))
                .add(binaryFlag(data.everDuplicatedCode()))
                .add(String.valueOf(data.maxCodeSmells()))
                .add(String.format(java.util.Locale.ROOT, "%.4f", data.maxSmellDensity()))
                .add(String.valueOf(data.touches()))
                .add(String.valueOf(data.totalTouches()))
                .add(String.valueOf(data.issueTouches()))
                .add(String.valueOf(data.totalIssueTouches()))
                .add(String.valueOf(data.authors()))
                .add(String.valueOf(data.totalAuthors()))
                .add(String.valueOf(data.addedLines()))
                .add(String.valueOf(data.deletedLines()))
                .add(String.valueOf(data.churn()))
                .add(String.valueOf(data.totalChurn()))
                .add(String.valueOf(data.prevCodeSmells()))
                .add(String.valueOf(data.ageInReleases()))
                .add(yesNo(data.prevBuggy()))
                .add(yesNo(data.buggy()));
        return joiner.toString();
    }

    /**
     * Quotes a CSV field escaping embedded double quotes.
     *
     * @param value raw field value
     * @return quoted CSV field
     */
    private static String quote(String value) {
        return '"' + value.replace("\"", "\"\"") + '"';
    }

    /**
     * Encodes a boolean smell flag as {@code 1} or {@code 0}.
     *
     * @param value boolean value to encode
     * @return numeric binary flag
     */
    private static String binaryFlag(boolean value) {
        return value ? "1" : "0";
    }

    /**
     * Encodes a boolean label as {@code yes} or {@code no}.
     *
     * @param value boolean value to encode
     * @return yes/no representation
     */
    private static String yesNo(boolean value) {
        return value ? "yes" : "no";
    }
}
