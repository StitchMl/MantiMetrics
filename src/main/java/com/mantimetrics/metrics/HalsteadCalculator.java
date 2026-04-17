package com.mantimetrics.metrics;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.LiteralExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.UnaryExpr;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static java.lang.Math.log;

/**
 * Computes Halstead metrics from JavaParser AST nodes.
 */
public final class HalsteadCalculator {

    /**
     * Computes Halstead metrics for a single AST root.
     *
     * @param root AST root to analyze
     * @return Halstead metrics
     */
    public HalsteadMetrics compute(Node root) {
        return compute(List.of(root));
    }

    /**
     * Computes Halstead metrics for multiple AST roots treated as one aggregate entity.
     *
     * @param roots AST roots to analyze
     * @return Halstead metrics
     */
    public HalsteadMetrics compute(Collection<? extends Node> roots) {
        Set<String> distinctOps = new HashSet<>();
        Set<String> distinctOperands = new HashSet<>();
        AtomicInteger totalOps = new AtomicInteger();
        AtomicInteger totalOperands = new AtomicInteger();

        for (Node root : roots) {
            root.walk(node -> {
                if (node instanceof BinaryExpr binaryExpr) {
                    distinctOps.add(binaryExpr.getOperator().asString());
                    totalOps.incrementAndGet();
                } else if (node instanceof UnaryExpr unaryExpr) {
                    distinctOps.add(unaryExpr.getOperator().asString());
                    totalOps.incrementAndGet();
                } else if (node instanceof AssignExpr assignExpr) {
                    distinctOps.add(assignExpr.getOperator().asString());
                    totalOps.incrementAndGet();
                } else if (node instanceof MethodCallExpr methodCallExpr) {
                    distinctOperands.add(methodCallExpr.getNameAsString());
                    totalOperands.incrementAndGet();
                } else if (node instanceof NameExpr nameExpr) {
                    distinctOperands.add(nameExpr.getNameAsString());
                    totalOperands.incrementAndGet();
                } else if (node instanceof LiteralExpr literalExpr) {
                    distinctOperands.add(literalExpr.toString());
                    totalOperands.incrementAndGet();
                }
            });
        }

        double n1 = distinctOps.size();
        int n2 = distinctOperands.size();
        double totalN1 = totalOps.get();
        int totalN2 = totalOperands.get();
        double vocabulary = n1 + n2;
        double length = totalN1 + totalN2;
        double volume = (vocabulary > 0) ? length * (log(vocabulary) / log(2)) : 0;
        double difficulty = (n1 > 0 && n2 > 0) ? (n1 / 2.0) * (totalN2 / (double) n2) : 0;
        double effort = difficulty * volume;

        return new HalsteadMetrics.Builder()
                .n1((int) n1)
                .n2(n2)
                .totalN1((int) totalN1)
                .totalN2(totalN2)
                .vocabulary(vocabulary)
                .length(length)
                .volume(volume)
                .difficulty(difficulty)
                .effort(effort)
                .build();
    }
}
