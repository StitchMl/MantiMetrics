package com.mantimetrics.metrics;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.*;
import com.github.javaparser.Range;
import com.mantimetrics.clone.CloneDetector;
import com.mantimetrics.clone.SourceCollectionException;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static java.lang.Math.log;

public class MetricsCalculator {

    /**
     * Computes a set of metrics for a given method.
     *
     * @param m the method declaration to analyze
     * @return a MethodMetrics object containing the computed metrics
     */
    public MethodMetrics computeAll(MethodDeclaration m) throws SourceCollectionException, IOException {
        MethodMetrics mm = new MethodMetrics();

        // 1. LOC
        Optional<Range> rangeOpt = m.getRange();
        int loc = rangeOpt.map(r -> r.end.line - r.begin.line + 1).orElse(0);
        mm.setLoc(loc);

        // 2. Statement count
        mm.setStmtCount(m.findAll(com.github.javaparser.ast.stmt.Statement.class).size());

        // 3. Cyclomatic & Cognitive
        mm.setCyclomatic(computeCyclomatic(m));
        mm.setCognitive(computeCognitive(m));

        // 4. Halstead
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

        // 5. Nesting depth
        mm.setMaxNestingDepth(computeMaxNestingDepth(m, 0));

        // 6. Code smells
        mm.setLongMethod(loc > 50);
        detectFeatureEnvy(m, mm);
        mm.setGodClass(detectGodClass(m));
        mm.setDuplicatedCode(detectDuplicatedCode(m));

        return mm;
    }

    // --- Metriche di complessità ---

    /**
     * Computes the cyclomatic complexity of a method.
     *
     * @param m the method declaration to analyze
     * @return the cyclomatic complexity of the method
     */
    private int computeCyclomatic(MethodDeclaration m) {
        int decisions = m.findAll(IfStmt.class).size()
                + m.findAll(ForStmt.class).size()
                + m.findAll(WhileStmt.class).size()
                + m.findAll(DoStmt.class).size()
                + m.findAll(SwitchEntry.class).size()
                + m.findAll(ConditionalExpr.class).size();
        return decisions + 1;
    }

    /**
     * Computes the cognitive complexity of a method.
     *
     * @param m the method declaration to analyze
     * @return the cognitive complexity of the method
     */
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

    // --- Halstead ---

    /**
     * Computes Halstead metrics for a method.
     *
     * @param m the method declaration to analyze
     * @return a HalsteadMetrics object containing the computed metrics
     */
    private HalsteadMetrics computeHalstead(MethodDeclaration m) {
        Set<String> distinctOps = new HashSet<>();
        Set<String> distinctOpr = new HashSet<>();
        AtomicInteger totalOps = new AtomicInteger();
        AtomicInteger totalOpr = new AtomicInteger();

        m.walk(node -> {
            if (node instanceof BinaryExpr binaryexpr) {
                String op = binaryexpr.getOperator().asString();
                distinctOps.add(op);
                totalOps.incrementAndGet();
            } else if (node instanceof UnaryExpr unaryexpr) {
                String op = unaryexpr.getOperator().asString();
                distinctOps.add(op);
                totalOps.incrementAndGet();
            } else if (node instanceof AssignExpr assignexpr) {
                String op = assignexpr.getOperator().asString();
                distinctOps.add(op);
                totalOps.incrementAndGet();
            } else if (node instanceof MethodCallExpr methodcallexpr) {
                distinctOpr.add(methodcallexpr.getNameAsString());
                totalOpr.incrementAndGet();
            } else if (node instanceof NameExpr nameexpr) {
                distinctOpr.add(nameexpr.getNameAsString());
                totalOpr.incrementAndGet();
            } else if (node instanceof LiteralExpr literalexpr) {
                distinctOpr.add(literalexpr.toString());
                totalOpr.incrementAndGet();
            }
        });

        double n1       = distinctOps.size();
        int n2       = distinctOpr.size();
        double totalN1       = totalOps.get();
        int totalN2       = totalOpr.get();
        double vocab = n1 + n2;
        double len   = totalN1 + totalN2;
        double vol   = (vocab > 0) ? len * (log(vocab) / log(2)) : 0;
        double diff  = (n1 > 0 && n2 > 0) ? (n1 / 2.0) * (totalN2 / (double)n2) : 0;
        double eff   = diff * vol;

        return new HalsteadMetrics.Builder()
                .n1((int) n1)
                .n2(n2)
                .totalN1((int) totalN1)
                .totalN2(totalN2)
                .vocabulary(vocab)
                .length(len)
                .volume(vol)
                .difficulty(diff)
                .effort(eff)
                .build();
    }

    // --- Nesting Depth ---

    /**
     * Computes the maximum nesting depth of a method.
     *
     * @param node the method declaration to analyze
     * @param currentDepth the current nesting depth
     * @return the maximum nesting depth of the method
     */
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

    // --- Feature Envy detection ---

    /**
     * Detects Feature Envy code smell in a method.
     * Feature Envy occurs when a method accesses more fields or methods of another class than its own.
     *
     * @param m the method declaration to analyze
     * @param mm the MethodMetrics object to update with the result
     */
    private void detectFeatureEnvy(MethodDeclaration m, MethodMetrics mm) {
        AtomicInteger internal = new AtomicInteger();
        AtomicInteger external = new AtomicInteger();
        m.walk(node -> {
            if (node instanceof FieldAccessExpr fa) {
                // this.x or super.x → internal, otherwise external
                String scope = fa.getScope().toString();
                if ("this".equals(scope) || "super".equals(scope)) internal.getAndIncrement();
                else external.getAndIncrement();
            }
            else if (node instanceof MethodCallExpr mc && mc.getScope().isPresent()) {
                String scope = mc.getScope().get().toString();
                if ("this".equals(scope) || "super".equals(scope)) internal.getAndIncrement();
                else external.getAndIncrement();
            }
        });
        mm.setFeatureEnvy(external.get() > internal.get());
    }

    // --- God Class Survey ---

    /**
     * Detects God Class code smell in a method.
     * A God Class is a class that has too many responsibilities, making it hard to maintain.
     *
     * @param m the method declaration to analyze
     * @return true if the method belongs to a God Class, false otherwise
     */
    private boolean detectGodClass(MethodDeclaration m) {
        // Find the class declaration containing the method
        Optional<ClassOrInterfaceDeclaration> clsOpt =
                m.findCompilationUnit()
                        .flatMap(cu -> cu.findFirst(ClassOrInterfaceDeclaration.class));
        if (clsOpt.isEmpty()) return false;
        ClassOrInterfaceDeclaration cls = clsOpt.get();

        // 1) WMC = sum of cyclomatic complexity of all methods
        int wmc = cls.findAll(MethodDeclaration.class)
                .stream()
                .mapToInt(this::computeCyclomatic)
                .sum();

        // 2) ATFD = number of accesses to external data
        int numAccess = cls.findAll(FieldAccessExpr.class).size();

        // 3) LCOM (e.g. LCOM4, calculated by an external helper)
        double lcom = new CohesionCalculator().calculateLcom4(cls);

        // Example thresholds: WMC > 20, ATFD > 5, LCOM > 0.8
        int soglieSuperate = 0;
        if (wmc > 20) soglieSuperate++;
        if (numAccess > 5) soglieSuperate++;
        if (lcom > 0.8) soglieSuperate++;
        return soglieSuperate >= 2;
    }

    // --- Duplicated Code Detection ---

    /**
     * Detects duplicated code in a method using PMD CPD.
     * This method checks if the given method is a clone of another method.
     *
     * @param m the method declaration to analyze
     * @return true if the method is duplicated, false otherwise
     */
    private boolean detectDuplicatedCode(MethodDeclaration m) throws SourceCollectionException, IOException {
        // Wrapper su PMD CPD che analizza AST e individua cloni
        return CloneDetector.isMethodDuplicated(m);
    }
}