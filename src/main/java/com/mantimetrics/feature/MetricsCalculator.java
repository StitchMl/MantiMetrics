package com.mantimetrics.feature;

import com.github.javaparser.Range;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.mantimetrics.utility.JavaTypeUtility;

/**
 * Computes the lean class-level metric set (LOC, WMC, LCOM) used by Milestone 1.
 */
public class MetricsCalculator {
    private final CyclomaticFeatureCalculator complexityCalculator = new CyclomaticFeatureCalculator();
    private final CohesionFeatureCalculator cohesionCalculator = new CohesionFeatureCalculator();

    /**
     * Computes the class-level metrics for one type declaration.
     *
     * @param type type declaration to analyze
     * @return aggregated class metrics (LOC, WMC, LCOM)
     */
    public ClassMetrics computeAll(TypeDeclaration<?> type) {
        int loc = type.getRange().map(this::lengthOf).orElse(0);
        int wmc = JavaTypeUtility.directExecutableNodes(type).stream()
                .mapToInt(complexityCalculator::cyclomatic)
                .sum();
        int lcom = cohesionCalculator.calculateLcom4(type);
        return new ClassMetrics(loc, wmc, lcom);
    }

    /**
     * Returns the inclusive line length of a source range.
     *
     * @param range JavaParser source range
     * @return inclusive line count
     */
    private int lengthOf(Range range) {
        return range.end.line - range.begin.line + 1;
    }
}
