package com.mantimetrics.metrics;

import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.mantimetrics.util.JavaTypeUtils;

/**
 * Applies a threshold-based heuristic to detect God Classes.
 */
public final class GodClassDetector {
    private final CohesionCalculator cohesionCalculator = new CohesionCalculator();
    private final ComplexityCalculator complexityCalculator;

    /**
     * Creates a God-Class detector using the provided complexity calculator.
     *
     * @param complexityCalculator complexity calculator used to compute WMC-like values
     */
    public GodClassDetector(ComplexityCalculator complexityCalculator) {
        this.complexityCalculator = complexityCalculator;
    }

    /**
     * Reports whether a type crosses enough thresholds to be considered a God Class.
     *
     * @param type type declaration to analyze
     * @return {@code true} when the type is considered a God Class
     */
    public boolean isGodClass(TypeDeclaration<?> type) {
        int wmc = JavaTypeUtils.directExecutableNodes(type).stream()
                .mapToInt(complexityCalculator::cyclomatic)
                .sum();

        int numAccess = JavaTypeUtils.directMetricNodes(type).stream()
                .mapToInt(node -> node.findAll(FieldAccessExpr.class).size())
                .sum();

        double lcom = cohesionCalculator.calculateLcom4(type);

        int thresholds = 0;
        if (wmc > 20) {
            thresholds++;
        }
        if (numAccess > 5) {
            thresholds++;
        }
        if (lcom > 0.8) {
            thresholds++;
        }
        return thresholds >= 2;
    }
}
