package com.mantimetrics.parser;

import com.mantimetrics.metrics.MetricsCalculator;
import com.mantimetrics.model.ClassData;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for class-level parsing in {@link CodeParser}.
 */
class CodeParserClassGranularityTest {

    /**
     * Verifies that class parsing includes supported top-level and nested type declarations.
     */
    @Test
    void parseClassesFromDirectoryIncludesRecordsEnumsAnnotationsAndNestedTypes() throws Exception {
        Path root = writeSampleSource();
        CodeParser parser = new CodeParser(null);

        List<ClassData> rows = parser.parseClassesFromDirectory(
                root,
                "sample-repo",
                "v1.0.0",
                new MetricsCalculator(),
                Map.of());

        List<String> names = rows.stream()
                .map(ClassData::getClassName)
                .sorted()
                .toList();

        assertEquals(
                List.of(
                        "sample.Data",
                        "sample.Marker",
                        "sample.Mode",
                        "sample.Outer",
                        "sample.Outer.Inner"),
                names);
    }

    /**
     * Verifies that the outer type metrics are not polluted by nested type declarations.
     */
    @Test
    void parseClassesFromDirectoryKeepsOuterMetricsIndependentFromNestedTypes() throws Exception {
        Path root = writeSampleSource();
        CodeParser parser = new CodeParser(null);

        List<ClassData> rows = parser.parseClassesFromDirectory(
                root,
                "sample-repo",
                "v1.0.0",
                new MetricsCalculator(),
                Map.of());

        ClassData outer = rows.stream()
                .filter(row -> row.getClassName().equals("sample.Outer"))
                .findFirst()
                .orElseThrow();
        ClassData inner = rows.stream()
                .filter(row -> row.getClassName().equals("sample.Outer.Inner"))
                .findFirst()
                .orElseThrow();

        assertEquals(2, outer.getMetrics().getCyclomatic());
        assertEquals(2, inner.getMetrics().getCyclomatic());
        assertTrue(outer.getMetrics().getStmtCount() < outer.getMetrics().getLoc());
    }

    /**
     * Creates a temporary source tree used by the class-granularity parser tests.
     *
     * @return root directory containing the sample source file
     * @throws Exception when the temporary source cannot be created
     */
    private Path writeSampleSource() throws Exception {
        Path root = Files.createTempDirectory("mantimetrics-class-test");
        Path sourceFile = root.resolve("sample").resolve("Example.java");
        Files.createDirectories(sourceFile.getParent());
        Files.writeString(sourceFile, """
                package sample;

                public class Outer {
                    void outerMethod() {
                        if (true) {
                            int x = 1;
                        }
                    }

                    class Inner {
                        void innerMethod() {
                            if (true) {
                                int y = 2;
                            }
                        }
                    }
                }

                record Data(int id) {
                    void recordMethod() {
                        int z = id;
                    }
                }

                enum Mode {
                    ON;

                    void enumMethod() {
                        int w = 4;
                    }
                }

                @interface Marker {
                }
                """);
        return root;
    }
}
