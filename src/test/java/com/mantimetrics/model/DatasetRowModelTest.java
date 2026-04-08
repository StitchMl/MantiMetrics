package com.mantimetrics.model;

import com.mantimetrics.metrics.MethodMetrics;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DatasetRowModelTest {

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
                .prevCodeSmells(1)
                .prevBuggy(false)
                .startLine(10)
                .endLine(20)
                .build();

        assertEquals(
                "repo,/src/main/java/com/acme/Foo.java/,\"void run(String value)\",v1,10,4,3,2,5,6,7,8,9.0,10.0,11.0,12.0,13.0,2,1,0,1,0,2,4,3,1,no,yes",
                method.toCsvLine());
    }

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
                .prevCodeSmells(2)
                .prevBuggy(true)
                .startLine(1)
                .endLine(30)
                .build();

        assertEquals(
                "repo,/src/main/java/com/acme/Foo.java/,\"com.acme.Foo\",v1,10,4,3,2,5,6,7,8,9.0,10.0,11.0,12.0,13.0,2,1,0,1,0,4,6,5,2,yes,no",
                type.toCsvLine());
    }

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
                .prevCodeSmells(3)
                .prevBuggy(true)
                .startLine(4)
                .endLine(8)
                .build();

        MethodData rebuilt = original.toBuilder().build();

        assertEquals(original.toCsvLine(), rebuilt.toCsvLine());
        assertEquals(original.getUniqueKey(), rebuilt.getUniqueKey());
        assertEquals(original.getCommitHashes(), rebuilt.getCommitHashes());
    }

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
