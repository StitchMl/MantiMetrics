package com.mantimetrics.dataset;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Generates the derived dataset artifacts starting from the raw CSV dataset.
 */
public final class DatasetArtifactService {
    private final DatasetCsvTableReader tableReader;
    private final DatasetTableWriter tableWriter;
    private final DatasetArffWriter arffWriter;
    private final DatasetMetadataWriter metadataWriter;
    private final WhatIfDatasetBuilder whatIfDatasetBuilder;

    /**
     * Creates the artifact service with the collaborators needed to read, split and serialize datasets.
     *
     * @param tableReader reader for raw CSV datasets
     * @param tableWriter writer for derived CSV datasets
     * @param arffWriter writer for derived ARFF datasets
     * @param metadataWriter writer for the artifact metadata file
     * @param whatIfDatasetBuilder builder for the A/B+/B/C dataset variants
     */
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

    /**
     * Generates all derived artifacts for a raw dataset.
     *
     * @param rawCsvPath raw dataset CSV path
     * @throws IOException when reading or writing any artifact fails
     */
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

    /**
     * Writes one derived dataset in both CSV and ARFF formats and records the produced paths.
     *
     * @param name dataset split name
     * @param table derived dataset table
     * @param rawCsvPath raw dataset CSV path
     * @param artifactDir artifact output directory
     * @param csvArtifacts output map of CSV artifact paths
     * @param arffArtifacts output map of ARFF artifact paths
     * @throws IOException when writing the artifact fails
     */
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

    /**
     * Resolves the directory used to store the dataset artifacts.
     *
     * @param rawCsvPath raw dataset CSV path
     * @return artifact directory path
     */
    private Path resolveArtifactDirectory(Path rawCsvPath) {
        String baseName = stripExtension(rawCsvPath.getFileName().toString());
        return rawCsvPath.getParent().resolve(baseName + "_artifacts");
    }

    /**
     * Builds the ARFF relation name associated with one derived dataset split.
     *
     * @param rawCsvPath raw dataset CSV path
     * @param splitName dataset split name
     * @return ARFF relation name
     */
    private String relationName(Path rawCsvPath, String splitName) {
        return stripExtension(rawCsvPath.getFileName().toString()) + "_" + splitName;
    }

    /**
     * Removes the file extension from a filename when present.
     *
     * @param fileName filename to normalize
     * @return filename without extension
     */
    private String stripExtension(String fileName) {
        int lastDot = fileName.lastIndexOf('.');
        return lastDot >= 0 ? fileName.substring(0, lastDot) : fileName;
    }
}
