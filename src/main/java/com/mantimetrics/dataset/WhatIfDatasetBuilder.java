package com.mantimetrics.dataset;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Builds the milestone what-if dataset variants A, B+, B and C from the raw dataset.
 */
@SuppressWarnings("ClassEscapesDefinedScope")
public final class WhatIfDatasetBuilder {

    /**
     * Builds the classifier-ready and what-if dataset variants from the raw dataset.
     *
     * @param rawDataset raw dataset emitted by the extraction pipeline
     * @return derived dataset variants
     */
    public WhatIfDatasets build(DatasetTable rawDataset) {
        DatasetTable classifierReady = rawDataset.selectColumns(DatasetColumns.classifierColumns(rawDataset.header()));
        requireColumn(classifierReady, DatasetColumns.NSMELLS);

        DatasetTable datasetBPlus = classifierReady.filter(this::containsSmells);
        DatasetTable datasetC = classifierReady.filter(row -> !containsSmells(row));
        DatasetTable datasetB = datasetBPlus.mapRows(this::zeroActionableColumns);

        return new WhatIfDatasets(classifierReady, datasetBPlus, datasetB, datasetC);
    }

    /**
     * Reports whether a row contains at least one smell.
     *
     * @param row dataset row
     * @return {@code true} when the row contains smells
     */
    private boolean containsSmells(Map<String, String> row) {
        return parseInt(row.get(DatasetColumns.NSMELLS)) > 0;
    }

    /**
     * Produces the B split row by zeroing the actionable smell-related columns.
     *
     * @param row source dataset row
     * @return mutated row for dataset B
     */
    private Map<String, String> zeroActionableColumns(Map<String, String> row) {
        Map<String, String> mutated = new LinkedHashMap<>(row);
        for (String column : DatasetColumns.actionableColumns()) {
            if (mutated.containsKey(column)) {
                mutated.put(column, "0");
            }
        }
        return mutated;
    }

    /**
     * Parses an integer cell value treating blanks as zero.
     *
     * @param raw raw cell value
     * @return parsed integer or {@code 0} for blanks
     */
    private int parseInt(String raw) {
        return raw == null || raw.isBlank() ? 0 : Integer.parseInt(raw.trim());
    }

    /**
     * Verifies that a required dataset column is present.
     *
     * @param table dataset table to inspect
     * @param column required column name
     */
    @SuppressWarnings("SameParameterValue")
    private void requireColumn(DatasetTable table, String column) {
        if (!table.header().contains(column)) {
            throw new IllegalArgumentException("Required dataset column missing: " + column);
        }
    }
}
