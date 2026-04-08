package com.mantimetrics.metrics;

import com.github.javaparser.Range;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.stmt.Statement;
import com.mantimetrics.clone.CloneDetector;
import com.mantimetrics.util.JavaTypeUtils;

import java.io.IOException;
import java.util.List;

public class MetricsCalculator {
    private final ComplexityCalculator complexityCalculator = new ComplexityCalculator();
    private final HalsteadCalculator halsteadCalculator = new HalsteadCalculator();
    private final FeatureEnvyDetector featureEnvyDetector = new FeatureEnvyDetector();
    private final GodClassDetector godClassDetector = new GodClassDetector(complexityCalculator);

    public MethodMetrics computeAll(MethodDeclaration method, String cloneCacheKey, String relUnixPath)
            throws IOException {
        int loc = method.getRange().map(this::lengthOf).orElse(0);
        return MethodMetrics.builder()
                .loc(loc)
                .stmtCount(method.findAll(Statement.class).size())
                .cyclomatic(complexityCalculator.cyclomatic(method))
                .cognitive(complexityCalculator.cognitive(method))
                .halstead(halsteadCalculator.compute(method))
                .maxNestingDepth(complexityCalculator.maxNestingDepth(method))
                .longMethod(loc > 50)
                .featureEnvy(featureEnvyDetector.isFeatureEnvy(method))
                .godClass(detectGodClass(method))
                .duplicatedCode(detectDuplicatedCode(method, cloneCacheKey, relUnixPath))
                .build();
    }

    public MethodMetrics computeAll(TypeDeclaration<?> type, String cloneCacheKey, String relUnixPath) {
        List<Node> directMetricNodes = JavaTypeUtils.directMetricNodes(type);
        List<Node> directExecutableNodes = JavaTypeUtils.directExecutableNodes(type);
        List<MethodDeclaration> directMethods = JavaTypeUtils.directMethods(type);
        return MethodMetrics.builder()
                .loc(type.getRange().map(this::lengthOf).orElse(0))
                .stmtCount(directMetricNodes.stream()
                        .mapToInt(node -> node.findAll(Statement.class).size())
                        .sum())
                .cyclomatic(directExecutableNodes.stream()
                        .mapToInt(complexityCalculator::cyclomatic)
                        .sum())
                .cognitive(directExecutableNodes.stream()
                        .mapToInt(complexityCalculator::cognitive)
                        .sum())
                .halstead(halsteadCalculator.compute(directMetricNodes))
                .maxNestingDepth(directExecutableNodes.stream()
                        .mapToInt(complexityCalculator::maxNestingDepth)
                        .max()
                        .orElse(0))
                .longMethod(directExecutableNodes.stream()
                        .anyMatch(node -> node.getRange().map(this::lengthOf).orElse(0) > 50))
                .featureEnvy(directMethods.stream().anyMatch(featureEnvyDetector::isFeatureEnvy))
                .godClass(godClassDetector.isGodClass(type))
                .duplicatedCode(hasDuplicatedMethod(directMethods, cloneCacheKey, relUnixPath))
                .build();
    }

    private boolean hasDuplicatedMethod(
            List<MethodDeclaration> methods,
            String cloneCacheKey,
            String relUnixPath
    ) {
        for (MethodDeclaration method : methods) {
            try {
                if (detectDuplicatedCode(method, cloneCacheKey, relUnixPath)) {
                    return true;
                }
            } catch (IOException ignored) {
                // Best effort: duplicate detection should not discard the whole type.
            }
        }
        return false;
    }

    private boolean detectGodClass(MethodDeclaration method) {
        return JavaTypeUtils.enclosingType(method)
                .map(godClassDetector::isGodClass)
                .orElse(false);
    }

    private boolean detectDuplicatedCode(MethodDeclaration method, String cloneCacheKey, String relUnixPath)
            throws IOException {
        return CloneDetector.isMethodDuplicated(cloneCacheKey, relUnixPath, method);
    }

    private int lengthOf(Range range) {
        return range.end.line - range.begin.line + 1;
    }
}
