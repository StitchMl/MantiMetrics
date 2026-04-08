package com.mantimetrics.dataset;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public final class DatasetMetadataWriter {
    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    @SuppressWarnings("ClassEscapesDefinedScope")
    public void write(
            Path outputPath,
            Path rawCsvPath,
            WhatIfDatasets datasets,
            Map<String, Path> csvArtifacts,
            Map<String, Path> arffArtifacts
    ) throws IOException {
        Files.createDirectories(outputPath.getParent());

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("rawDataset", rawCsvPath.toString());
        metadata.put("identifierColumns", DatasetColumns.identifierColumns());
        metadata.put("featureColumns", datasets.datasetA().header().stream()
                .filter(column -> !DatasetColumns.BUGGY.equals(column))
                .toList());
        metadata.put("actionableColumns", DatasetColumns.actionableColumns());
        metadata.put("labelColumn", DatasetColumns.BUGGY);

        Map<String, Object> artifacts = new LinkedHashMap<>();
        artifacts.put("A", artifactEntry(csvArtifacts.get("A"), arffArtifacts.get("A"), datasets.datasetA().rowCount()));
        artifacts.put("BPlus", artifactEntry(csvArtifacts.get("BPlus"), arffArtifacts.get("BPlus"), datasets.datasetBPlus().rowCount()));
        artifacts.put("B", artifactEntry(csvArtifacts.get("B"), arffArtifacts.get("B"), datasets.datasetB().rowCount()));
        artifacts.put("C", artifactEntry(csvArtifacts.get("C"), arffArtifacts.get("C"), datasets.datasetC().rowCount()));
        metadata.put("derivedDatasets", artifacts);

        JSON.writeValue(outputPath.toFile(), metadata);
    }

    private Map<String, Object> artifactEntry(Path csvPath, Path arffPath, int rowCount) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("csv", csvPath.toString());
        entry.put("arff", arffPath.toString());
        entry.put("rows", rowCount);
        return entry;
    }
}
