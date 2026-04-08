package com.mantimetrics.metrics;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.ConditionalExpr;
import com.github.javaparser.ast.stmt.DoStmt;
import com.github.javaparser.ast.stmt.ForStmt;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.SwitchEntry;
import com.github.javaparser.ast.stmt.SwitchStmt;
import com.github.javaparser.ast.stmt.WhileStmt;

public final class ComplexityCalculator {

    public int cyclomatic(Node node) {
        int decisions = node.findAll(IfStmt.class).size()
                + node.findAll(ForStmt.class).size()
                + node.findAll(WhileStmt.class).size()
                + node.findAll(DoStmt.class).size()
                + node.findAll(SwitchEntry.class).size()
                + node.findAll(ConditionalExpr.class).size();
        return decisions + 1;
    }

    public int cognitive(Node node) {
        return node.findAll(Node.class).stream()
                .mapToInt(n -> (n instanceof IfStmt
                        || n instanceof ForStmt
                        || n instanceof WhileStmt
                        || n instanceof DoStmt
                        || n instanceof SwitchStmt) ? 1 : 0)
                .sum();
    }

    public int maxNestingDepth(Node node) {
        return maxNestingDepth(node, 0);
    }

    private int maxNestingDepth(Node node, int currentDepth) {
        int max = currentDepth;
        for (Node child : node.getChildNodes()) {
            int nextDepth = currentDepth;
            if (child instanceof IfStmt
                    || child instanceof ForStmt
                    || child instanceof WhileStmt
                    || child instanceof DoStmt
                    || child instanceof SwitchStmt) {
                nextDepth = currentDepth + 1;
            }
            max = Math.max(max, maxNestingDepth(child, nextDepth));
        }
        return max;
    }
}
