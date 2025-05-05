package com.mantimetrics.metrics;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.*;
import com.github.javaparser.Range;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static java.lang.Math.log;

public class MetricsCalculator {

    public MethodMetrics computeAll(MethodDeclaration m) {
        MethodMetrics mm = new MethodMetrics();

        // 1. LOC: use Optional<Range> instead of getBegin().get()/getEnd().get()
        Optional<Range> rangeOpt = m.getRange();
        int loc = rangeOpt.map(r -> r.end.line - r.begin.line + 1).orElse(0);
        mm.setLoc(loc);

        // 2. Statement count
        mm.setStmtCount(m.findAll(com.github.javaparser.ast.stmt.Statement.class).size());

        // 3. Cyclomatic & Cognitive
        mm.setCyclomatic(computeCyclomatic(m));
        mm.setCognitive(computeCognitive(m));

        // 4. Cognitive complexity (simple proxy)
        mm.setCognitive(computeCognitive(m));

        // 5. Halstead metrics via Builder
        HalsteadMetrics h = computeHalstead(m);
        mm.setDistinctOperators(h.getDistinctOperators());
        mm.setDistinctOperands(h.getDistinctOperands());
        mm.setTotalOperators(h.getTotalOperators());
        mm.setTotalOperands(h.getTotalOperands());
        mm.setVocabulary(h.getVocabulary());
        mm.setLength(h.getLength());
        mm.setVolume(h.getVolume());
        mm.setDifficulty(h.getDifficulty());
        mm.setEffort(h.getEffort());

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

        m.walk(node -> {
            if (node instanceof BinaryExpr) {
                String op = ((BinaryExpr) node).getOperator().asString();
                distinctOps.add(op); totalOps.incrementAndGet();
            } else if (node instanceof UnaryExpr) {
                String op = ((UnaryExpr) node).getOperator().asString();
                distinctOps.add(op); totalOps.incrementAndGet();
            } else if (node instanceof AssignExpr) {
                String op = ((AssignExpr) node).getOperator().asString();
                distinctOps.add(op); totalOps.incrementAndGet();
            } else if (node instanceof MethodCallExpr) {
                distinctOpr.add(((MethodCallExpr) node).getNameAsString());
                totalOpr.incrementAndGet();
            } else if (node instanceof NameExpr) {
                distinctOpr.add(((NameExpr) node).getNameAsString());
                totalOpr.incrementAndGet();
            } else if (node instanceof LiteralExpr) {
                distinctOpr.add(node.toString());
                totalOpr.incrementAndGet();
            }
        });

        int n1       = distinctOps.size();
        int n2       = distinctOpr.size();
        int totalN1       = totalOps.get();
        int totalN2       = totalOpr.get();
        double vocab = n1 + n2;
        double len   = totalN1 + totalN2;
        double vol   = (vocab > 0) ? len * (log(vocab) / log(2)) : 0;
        double diff  = (n1 > 0 && n2 > 0) ? (n1 / 2.0) * (totalN2 / (double)n2) : 0;
        double eff   = diff * vol;

        return new HalsteadMetrics.Builder()
                .n1(n1)
                .n2(n2)
                .totalN1(totalN1)
                .totalN2(totalN2)
                .vocabulary(vocab)
                .length(len)
                .volume(vol)
                .difficulty(diff)
                .effort(eff)
                .build();
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
}