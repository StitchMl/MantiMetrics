package com.mantimetrics.metrics;

import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;

import java.util.concurrent.atomic.AtomicInteger;

public final class FeatureEnvyDetector {

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
