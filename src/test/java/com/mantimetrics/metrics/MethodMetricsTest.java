package com.mantimetrics.metrics;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MethodMetricsTest {

    @Test
    void builderProducesValueObjectWithStableEquality() {
        MethodMetrics left = MethodMetrics.builder()
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

        MethodMetrics right = MethodMetrics.builder()
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

        assertEquals(left, right);
        assertEquals(left.hashCode(), right.hashCode());
    }
}
