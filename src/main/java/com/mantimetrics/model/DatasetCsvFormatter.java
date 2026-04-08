package com.mantimetrics.model;

import java.util.StringJoiner;

final class DatasetCsvFormatter {

    private DatasetCsvFormatter() {
        throw new AssertionError("Do not instantiate DatasetCsvFormatter");
    }

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
                .add(String.valueOf(data.touches()))
                .add(String.valueOf(data.prevCodeSmells()))
                .add(yesNo(data.prevBuggy()))
                .add(yesNo(data.buggy()));
        return joiner.toString();
    }

    private static String quote(String value) {
        return '"' + value.replace("\"", "\"\"") + '"';
    }

    private static String binaryFlag(boolean value) {
        return value ? "1" : "0";
    }

    private static String yesNo(boolean value) {
        return value ? "yes" : "no";
    }
}
