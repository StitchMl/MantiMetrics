package com.mantimetrics.metrics;

import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.mantimetrics.util.JavaTypeUtils;

public final class GodClassDetector {
    private final CohesionCalculator cohesionCalculator = new CohesionCalculator();
    private final ComplexityCalculator complexityCalculator;

    public GodClassDetector(ComplexityCalculator complexityCalculator) {
        this.complexityCalculator = complexityCalculator;
    }

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
