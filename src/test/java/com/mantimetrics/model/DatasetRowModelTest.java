package com.mantimetrics.model;

import com.mantimetrics.metrics.MethodMetrics;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests for the dataset row model classes.
 */
class DatasetRowModelTest {

    /**
     * Verifies that method rows preserve the expected CSV serialization format.
     */
    @Test
    void methodDataKeepsCsvFormatStable() {
        MethodData method = new MethodData.Builder()
                .projectName("repo")
                .path("/src/main/java/com/acme/Foo.java/")
                .methodSignature("void run(String value)")
                .releaseId("v1")
                .metrics(sampleMetrics())
                .commitHashes(List.of("abc"))
                .buggy(true)
                .codeSmells(2)
                .touches(3)
                .totalTouches(5)
                .issueTouches(1)
                .totalIssueTouches(2)
                .authors(1)
                .totalAuthors(2)
                .addedLines(10)
                .deletedLines(4)
                .churn(14)
                .totalChurn(20)
                .prevCodeSmells(1)
                .ageInReleases(2)
                .prevBuggy(false)
                .startLine(10)
                .endLine(20)
                .build();

        assertEquals(
                "repo,/src/main/java/com/acme/Foo.java/,\"void run(String value)\",v1,10,4,3,2,5,6,7,8,9.0,10.0,11.0,12.0,13.0,2,1,0,1,0,2,4,3,5,1,2,1,2,10,4,14,20,1,2,no,yes",
                method.toCsvLine());
    }

    /**
     * Verifies that class rows preserve the expected CSV serialization format.
     */
    @Test
    void classDataKeepsCsvFormatStable() {
        ClassData type = new ClassData.Builder()
                .projectName("repo")
                .path("/src/main/java/com/acme/Foo.java/")
                .className("com.acme.Foo")
                .releaseId("v1")
                .metrics(sampleMetrics())
                .commitHashes(List.of("abc"))
                .buggy(false)
                .codeSmells(4)
                .touches(5)
                .totalTouches(8)
                .issueTouches(2)
                .totalIssueTouches(3)
                .authors(1)
                .totalAuthors(2)
                .addedLines(9)
                .deletedLines(1)
                .churn(10)
                .totalChurn(15)
                .prevCodeSmells(2)
                .ageInReleases(3)
                .prevBuggy(true)
                .startLine(1)
                .endLine(30)
                .build();

        assertEquals(
                "repo,/src/main/java/com/acme/Foo.java/,\"com.acme.Foo\",v1,10,4,3,2,5,6,7,8,9.0,10.0,11.0,12.0,13.0,2,1,0,1,0,4,6,5,8,2,3,1,2,9,1,10,15,2,3,yes,no",
                type.toCsvLine());
    }

    /**
     * Verifies that rebuilding a method row through {@code toBuilder()} preserves its state.
     */
    @Test
    void toBuilderPreservesMethodDataState() {
        MethodData original = new MethodData.Builder()
                .projectName("repo")
                .path("/src/main/java/com/acme/Foo.java/")
                .methodSignature("void run()")
                .releaseId("v1")
                .metrics(sampleMetrics())
                .commitHashes(List.of("abc"))
                .buggy(true)
                .codeSmells(1)
                .touches(2)
                .totalTouches(4)
                .issueTouches(1)
                .totalIssueTouches(1)
                .authors(1)
                .totalAuthors(1)
                .addedLines(3)
                .deletedLines(2)
                .churn(5)
                .totalChurn(5)
                .prevCodeSmells(3)
                .ageInReleases(1)
                .prevBuggy(true)
                .startLine(4)
                .endLine(8)
                .build();

        MethodData rebuilt = original.toBuilder().build();

        assertEquals(original.toCsvLine(), rebuilt.toCsvLine());
        assertEquals(original.getUniqueKey(), rebuilt.getUniqueKey());
        assertEquals(original.getCommitHashes(), rebuilt.getCommitHashes());
    }

    /**
     * Returns a representative metrics aggregate reused by the dataset row tests.
     *
     * @return sample method metrics
     */
    private MethodMetrics sampleMetrics() {
        return MethodMetrics.builder()
                .loc(10)
                .stmtCount(4)
                .cyclomatic(3)
                .cognitive(2)
                .distinctOperators(5)
                .distinctOperands(6)
                .totalOperators(7)
                .totalOperands(8)
                .vocabulary(9)
                .length(10)
                .volume(11)
                .difficulty(12)
                .effort(13)
                .maxNestingDepth(2)
                .longMethod(true)
                .godClass(false)
                .featureEnvy(true)
                .duplicatedCode(false)
                .build();
    }
}
