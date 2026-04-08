package com.mantimetrics.dataset;

import java.util.LinkedHashMap;
import java.util.Map;

@SuppressWarnings("ClassEscapesDefinedScope")
public final class WhatIfDatasetBuilder {

    public WhatIfDatasets build(DatasetTable rawDataset) {
        DatasetTable classifierReady = rawDataset.selectColumns(DatasetColumns.classifierColumns(rawDataset.header()));
        requireColumn(classifierReady, DatasetColumns.NSMELLS);

        DatasetTable datasetBPlus = classifierReady.filter(this::containsSmells);
        DatasetTable datasetC = classifierReady.filter(row -> !containsSmells(row));
        DatasetTable datasetB = datasetBPlus.mapRows(this::zeroActionableColumns);

        return new WhatIfDatasets(classifierReady, datasetBPlus, datasetB, datasetC);
    }

    private boolean containsSmells(Map<String, String> row) {
        return parseInt(row.get(DatasetColumns.NSMELLS)) > 0;
    }

    private Map<String, String> zeroActionableColumns(Map<String, String> row) {
        Map<String, String> mutated = new LinkedHashMap<>(row);
        for (String column : DatasetColumns.actionableColumns()) {
            if (mutated.containsKey(column)) {
                mutated.put(column, "0");
            }
        }
        return mutated;
    }

    private int parseInt(String raw) {
        return raw == null || raw.isBlank() ? 0 : Integer.parseInt(raw.trim());
    }

    @SuppressWarnings("SameParameterValue")
    private void requireColumn(DatasetTable table, String column) {
        if (!table.header().contains(column)) {
            throw new IllegalArgumentException("Required dataset column missing: " + column);
        }
    }
}
