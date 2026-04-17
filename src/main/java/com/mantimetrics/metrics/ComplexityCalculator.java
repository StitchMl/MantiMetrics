package com.mantimetrics.metrics;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.ConditionalExpr;
import com.github.javaparser.ast.stmt.DoStmt;
import com.github.javaparser.ast.stmt.ForStmt;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.SwitchEntry;
import com.github.javaparser.ast.stmt.SwitchStmt;
import com.github.javaparser.ast.stmt.WhileStmt;

/**
 * Computes structural complexity metrics from JavaParser AST nodes.
 */
public final class ComplexityCalculator {

    /**
     * Computes the cyclomatic complexity of a node.
     *
     * @param node AST node to analyze
     * @return cyclomatic complexity
     */
    public int cyclomatic(Node node) {
        int decisions = node.findAll(IfStmt.class).size()
                + node.findAll(ForStmt.class).size()
                + node.findAll(WhileStmt.class).size()
                + node.findAll(DoStmt.class).size()
                + node.findAll(SwitchEntry.class).size()
                + node.findAll(ConditionalExpr.class).size();
        return decisions + 1;
    }

    /**
     * Computes a lightweight cognitive complexity score by counting control structures.
     *
     * @param node AST node to analyze
     * @return cognitive complexity score
     */
    public int cognitive(Node node) {
        return node.findAll(Node.class).stream()
                .mapToInt(n -> (n instanceof IfStmt
                        || n instanceof ForStmt
                        || n instanceof WhileStmt
                        || n instanceof DoStmt
                        || n instanceof SwitchStmt) ? 1 : 0)
                .sum();
    }

    /**
     * Computes the maximum nesting depth of control structures inside a node.
     *
     * @param node AST node to analyze
     * @return maximum nesting depth
     */
    public int maxNestingDepth(Node node) {
        return maxNestingDepth(node, 0);
    }

    /**
     * Recursive implementation used to compute the maximum control-structure nesting depth.
     *
     * @param node current AST node
     * @param currentDepth current nesting depth
     * @return maximum nesting depth found below the node
     */
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
