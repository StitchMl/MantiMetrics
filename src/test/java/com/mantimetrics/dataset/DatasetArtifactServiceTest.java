package com.mantimetrics.dataset;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link DatasetArtifactService}.
 */
class DatasetArtifactServiceTest {

    @TempDir
    Path tempDir;

    /**
     * Verifies that the service generates the full set of exam-friendly dataset artifacts from the raw CSV.
     */
    @Test
    void generatesExamFriendlyArtifactsFromRawDataset() throws IOException {
        Path rawCsv = tempDir.resolve("repo_dataset_method.csv");
        Files.writeString(rawCsv, rawDatasetContent(), StandardCharsets.UTF_8);

        DatasetArtifactService service = new DatasetArtifactService(
                new DatasetCsvTableReader(),
                new DatasetTableWriter(),
                new DatasetArffWriter(),
                new DatasetMetadataWriter(),
                new WhatIfDatasetBuilder()
        );

        service.generate(rawCsv);

        Path artifactDir = tempDir.resolve("repo_dataset_method_artifacts");
        Path datasetA = artifactDir.resolve("A.csv");
        Path datasetBPlus = artifactDir.resolve("BPlus.csv");
        Path datasetB = artifactDir.resolve("B.csv");
        Path datasetC = artifactDir.resolve("C.csv");
        Path arffA = artifactDir.resolve("A.arff");
        Path metadata = artifactDir.resolve("metadata.json");

        assertTrue(Files.exists(datasetA));
        assertTrue(Files.exists(datasetBPlus));
        assertTrue(Files.exists(datasetB));
        assertTrue(Files.exists(datasetC));
        assertTrue(Files.exists(arffA));
        assertTrue(Files.exists(metadata));

        List<String> aLines = Files.readAllLines(datasetA, StandardCharsets.UTF_8);
        assertEquals("LOC,StmtCount,Cyclomatic,Cognitive,DistinctOperators,DistinctOperands,TotalOperators,TotalOperands,Vocabulary,Length,Volume,Difficulty,Effort,MaxNestingDepth,isLongMethod,isGodClass,isFeatureEnvy,isDuplicatedCode,CodeSmells,NSmells,Touches,TotalTouches,IssueTouches,TotalIssueTouches,Authors,TotalAuthors,AddedLines,DeletedLines,Churn,TotalChurn,prevCodeSmells,AgeInReleases,prevBuggy,Buggy", aLines.get(0));
        assertEquals(3, aLines.size());

        List<String> bPlusLines = Files.readAllLines(datasetBPlus, StandardCharsets.UTF_8);
        assertEquals(2, bPlusLines.size());

        List<String> bLines = Files.readAllLines(datasetB, StandardCharsets.UTF_8);
        assertEquals("10,4,3,2,5,6,7,8,9.0,10.0,11.0,12.0,13.0,2,0,0,0,0,0,0,3,5,1,2,1,2,10,4,14,20,1,2,no,yes", bLines.get(1));

        List<String> cLines = Files.readAllLines(datasetC, StandardCharsets.UTF_8);
        assertEquals(2, cLines.size());

        String arffContent = Files.readString(arffA, StandardCharsets.UTF_8);
        assertTrue(arffContent.contains("@relation 'repo_dataset_method_A'"));
        assertTrue(arffContent.contains("@attribute 'Buggy' {yes,no}"));

        String metadataContent = Files.readString(metadata, StandardCharsets.UTF_8);
        assertTrue(metadataContent.contains("\"actionableColumns\""));
        assertTrue(metadataContent.contains("\"BPlus\""));
    }

    /**
     * Returns a representative raw dataset CSV payload used by the artifact-generation test.
     *
     * @return raw dataset CSV content
     */
    private String rawDatasetContent() {
        return String.join(System.lineSeparator(),
                "Project,Path,Method,ReleaseId,LOC,StmtCount,Cyclomatic,Cognitive,DistinctOperators,DistinctOperands,TotalOperators,TotalOperands,Vocabulary,Length,Volume,Difficulty,Effort,MaxNestingDepth,isLongMethod,isGodClass,isFeatureEnvy,isDuplicatedCode,CodeSmells,NSmells,Touches,TotalTouches,IssueTouches,TotalIssueTouches,Authors,TotalAuthors,AddedLines,DeletedLines,Churn,TotalChurn,prevCodeSmells,AgeInReleases,prevBuggy,Buggy",
                "repo,src/main/java/com/acme/Foo.java,\"void run()\",v1,10,4,3,2,5,6,7,8,9.0,10.0,11.0,12.0,13.0,2,1,0,1,0,2,4,3,5,1,2,1,2,10,4,14,20,1,2,no,yes",
                "repo,src/main/java/com/acme/Bar.java,\"void clean()\",v1,12,5,4,3,6,7,8,9,10.0,11.0,12.0,13.0,14.0,1,0,0,0,0,0,0,1,1,0,0,1,1,0,0,0,0,0,1,no,no",
                "");
    }
}
