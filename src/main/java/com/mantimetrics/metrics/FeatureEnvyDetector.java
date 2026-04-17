package com.mantimetrics.metrics;

import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Applies a simple heuristic to detect Feature Envy on method declarations.
 */
public final class FeatureEnvyDetector {

    /**
     * Reports whether a method accesses external members more often than its own members.
     *
     * @param method method declaration to analyze
     * @return {@code true} when the method is considered feature-envious
     */
    public boolean isFeatureEnvy(MethodDeclaration method) {
        AtomicInteger internal = new AtomicInteger();
        AtomicInteger external = new AtomicInteger();

        method.walk(node -> {
            if (node instanceof FieldAccessExpr fieldAccessExpr) {
                String scope = fieldAccessExpr.getScope().toString();
                if ("this".equals(scope) || "super".equals(scope)) {
                    internal.incrementAndGet();
                } else {
                    external.incrementAndGet();
                }
            } else if (node instanceof MethodCallExpr methodCallExpr && methodCallExpr.getScope().isPresent()) {
                String scope = methodCallExpr.getScope().get().toString();
                if ("this".equals(scope) || "super".equals(scope)) {
                    internal.incrementAndGet();
                } else {
                    external.incrementAndGet();
                }
            }
        });

        return external.get() > internal.get();
    }
}
