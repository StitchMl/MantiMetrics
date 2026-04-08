package com.mantimetrics.dataset;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public final class DatasetArtifactService {
    private final DatasetCsvTableReader tableReader;
    private final DatasetTableWriter tableWriter;
    private final DatasetArffWriter arffWriter;
    private final DatasetMetadataWriter metadataWriter;
    private final WhatIfDatasetBuilder whatIfDatasetBuilder;

    public DatasetArtifactService(
            DatasetCsvTableReader tableReader,
            DatasetTableWriter tableWriter,
            DatasetArffWriter arffWriter,
            DatasetMetadataWriter metadataWriter,
            WhatIfDatasetBuilder whatIfDatasetBuilder
    ) {
        this.tableReader = tableReader;
        this.tableWriter = tableWriter;
        this.arffWriter = arffWriter;
        this.metadataWriter = metadataWriter;
        this.whatIfDatasetBuilder = whatIfDatasetBuilder;
    }

    public void generate(Path rawCsvPath) throws IOException {
        DatasetTable rawDataset = tableReader.read(rawCsvPath);
        WhatIfDatasets datasets = whatIfDatasetBuilder.build(rawDataset);

        Path artifactDir = resolveArtifactDirectory(rawCsvPath);
        Files.createDirectories(artifactDir);

        Map<String, Path> csvArtifacts = new LinkedHashMap<>();
        Map<String, Path> arffArtifacts = new LinkedHashMap<>();
        writeArtifact("A", datasets.datasetA(), rawCsvPath, artifactDir, csvArtifacts, arffArtifacts);
        writeArtifact("BPlus", datasets.datasetBPlus(), rawCsvPath, artifactDir, csvArtifacts, arffArtifacts);
        writeArtifact("B", datasets.datasetB(), rawCsvPath, artifactDir, csvArtifacts, arffArtifacts);
        writeArtifact("C", datasets.datasetC(), rawCsvPath, artifactDir, csvArtifacts, arffArtifacts);

        metadataWriter.write(artifactDir.resolve("metadata.json"), rawCsvPath, datasets, csvArtifacts, arffArtifacts);
    }

    private void writeArtifact(
            String name,
            DatasetTable table,
            Path rawCsvPath,
            Path artifactDir,
            Map<String, Path> csvArtifacts,
            Map<String, Path> arffArtifacts
    ) throws IOException {
        Path csvPath = artifactDir.resolve(name + ".csv");
        Path arffPath = artifactDir.resolve(name + ".arff");
        tableWriter.write(csvPath, table);
        arffWriter.write(arffPath, relationName(rawCsvPath, name), table);
        csvArtifacts.put(name, csvPath);
        arffArtifacts.put(name, arffPath);
    }

    private Path resolveArtifactDirectory(Path rawCsvPath) {
        String baseName = stripExtension(rawCsvPath.getFileName().toString());
        return rawCsvPath.getParent().resolve(baseName + "_artifacts");
    }

    private String relationName(Path rawCsvPath, String splitName) {
        return stripExtension(rawCsvPath.getFileName().toString()) + "_" + splitName;
    }

    private String stripExtension(String fileName) {
        int lastDot = fileName.lastIndexOf('.');
        return lastDot >= 0 ? fileName.substring(0, lastDot) : fileName;
    }
}
