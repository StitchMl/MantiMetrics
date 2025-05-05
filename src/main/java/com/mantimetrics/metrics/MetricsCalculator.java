package com.mantimetrics.metrics;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import com.github.javaparser.Range;

import static java.lang.Math.log;

public class MetricsCalculator {

    public MethodMetrics computeAll(MethodDeclaration m) {
        MethodMetrics mm = new MethodMetrics();

        // 1. LOC: use Optional<Range> instead of getBegin().get()/getEnd().get()
        Optional<Range> rangeOpt = m.getRange();
        int loc = rangeOpt.map(r -> r.end.line - r.begin.line + 1).orElse(0);
        mm.setLoc(loc);  // :contentReference[oaicite:0]{index=0}

        // 2. Statement count
        mm.setStmtCount(m.findAll(com.github.javaparser.ast.stmt.Statement.class).size());

        // 3. Cyclomatic & Cognitive
        mm.setCyclomatic(computeCyclomatic(m));
        mm.setCognitive(computeCognitive(m));

        // 4. Cognitive complexity (simple proxy)
        mm.setCognitive(computeCognitive(m));

        // 5. Halstead metrics
        HalsteadMetrics h = computeHalstead(m);
        mm.setDistinctOperators(h.n1);
        mm.setDistinctOperands(h.n2);
        mm.setTotalOperators(h.total_n1);
        mm.setTotalOperands(h.total_n2);
        mm.setVocabulary(h.vocabulary);
        mm.setLength(h.length);
        mm.setVolume(h.volume);
        mm.setDifficulty(h.difficulty);
        mm.setEffort(h.effort);

        // 6. Nesting depth
        mm.setMaxNestingDepth(computeMaxNestingDepth(m, 0));

        // 7. Code smells (simple heuristics)
        mm.setLongMethod(loc > 50);
        mm.setFeatureEnvy(false);     // stub: must be implemented according to field access
        mm.setGodClass(false);        // not applicable to a single method
        mm.setDuplicatedCode(false);  // stub: requires cross-method analysis

        return mm;
    }

    private int computeCyclomatic(MethodDeclaration m) {
        int decisions = m.findAll(IfStmt.class).size()
                + m.findAll(ForStmt.class).size()
                + m.findAll(WhileStmt.class).size()
                + m.findAll(DoStmt.class).size()
                + m.findAll(SwitchEntry.class).size()
                + m.findAll(ConditionalExpr.class).size();
        return decisions + 1;
    }

    private int computeCognitive(MethodDeclaration m) {
        // Proxy: counts nested control blocks
        return m.findAll(Node.class).stream()
                .mapToInt(n -> (n instanceof IfStmt
                        || n instanceof ForStmt
                        || n instanceof WhileStmt
                        || n instanceof DoStmt
                        || n instanceof SwitchStmt) ? 1 : 0)
                .sum();
    }

    private HalsteadMetrics computeHalstead(MethodDeclaration m) {
        Set<String> distinctOps = new HashSet<>();
        Set<String> distinctOpr = new HashSet<>();
        AtomicInteger totalOps = new AtomicInteger();
        AtomicInteger totalOpr = new AtomicInteger();

        // Visitor on expressions
        m.walk(node -> {
            if (node instanceof BinaryExpr) {
                String op = ((BinaryExpr) node).getOperator().asString();
                distinctOps.add(op);
                totalOps.getAndIncrement();
            } else if (node instanceof UnaryExpr) {
                String op = ((UnaryExpr) node).getOperator().asString();
                distinctOps.add(op);
                totalOps.getAndIncrement();
            } else if (node instanceof AssignExpr) {
                String op = ((AssignExpr) node).getOperator().asString();
                distinctOps.add(op);
                totalOps.getAndIncrement();
            } else if (node instanceof MethodCallExpr) {
                distinctOpr.add(((MethodCallExpr) node).getNameAsString());
                totalOpr.getAndIncrement();
            } else if (node instanceof NameExpr) {
                distinctOpr.add(((NameExpr) node).getNameAsString());
                totalOpr.getAndIncrement();
            } else if (node instanceof LiteralExpr) {
                distinctOpr.add(node.toString());
                totalOpr.getAndIncrement();
            }
        });

        double n1 = distinctOps.size();
        int n2 = distinctOpr.size();
        double total_n1 = totalOps.get();
        int total_n2 = totalOpr.get();
        double vocabulary = n1 + n2;
        double length = total_n1 + total_n2;
        // to avoid log2(0)
        double volume = vocabulary > 0 ? length * (log(vocabulary) / log(2)) : 0;
        double difficulty = (n1 > 0 && n2 > 0) ? (n1 / 2.0) * (total_n2 / (double)n2) : 0;
        double effort = difficulty * volume;

        return new HalsteadMetrics((int) n1, n2, (int) total_n1, total_n2, vocabulary, length, volume, difficulty, effort);
    }

    private int computeMaxNestingDepth(Node node, int currentDepth) {
        int max = currentDepth;
        for (Node child : node.getChildNodes()) {
            int depth = currentDepth;
            if (child instanceof IfStmt
                    || child instanceof ForStmt
                    || child instanceof WhileStmt
                    || child instanceof DoStmt
                    || child instanceof SwitchStmt) {
                depth = currentDepth + 1;
            }
            max = Math.max(max, computeMaxNestingDepth(child, depth));
        }
        return max;
    }

    // Inner class to collect Halstead values
    private static class HalsteadMetrics {
        final int n1;
        final int n2;
        final int total_n1;
        final int total_n2;
        final double vocabulary;
        final double length;
        final double volume;
        final double difficulty;
        final double effort;
        HalsteadMetrics(int n1, int n2, int total_n1, int total_n2,
                        double vocabulary, double length,
                        double volume, double difficulty,
                        double effort) {
            this.n1 = n1; this.n2 = n2; this.total_n1 = total_n1; this.total_n2 = total_n2;
            this.vocabulary = vocabulary; this.length = length;
            this.volume = volume; this.difficulty = difficulty; this.effort = effort;
        }
    }
}