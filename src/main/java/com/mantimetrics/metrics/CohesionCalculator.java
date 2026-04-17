package com.mantimetrics.metrics;

import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.NameExpr;
import com.mantimetrics.util.JavaTypeUtils;

import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;

/**
 * Computes cohesion metrics for type declarations.
 */
public final class CohesionCalculator {

    /**
     * Computes the LCOM4 cohesion metric for a type declaration.
     *
     * @param type type declaration to analyze
     * @return LCOM4 component count
     */
    public int calculateLcom4(TypeDeclaration<?> type) {
        List<MethodDeclaration> methods = JavaTypeUtils.directMethods(type);
        List<FieldDeclaration> fields = JavaTypeUtils.directFields(type);

        if (methods.isEmpty()) {
            return 0;
        }

        Map<MethodDeclaration, Integer> methodIndexes = new IdentityHashMap<>();
        Map<String, Integer> fieldIndexes = new HashMap<>();
        int counter = 0;

        for (MethodDeclaration method : methods) {
            methodIndexes.put(method, counter++);
        }
        for (FieldDeclaration field : fields) {
            for (var variable : field.getVariables()) {
                fieldIndexes.put(variable.getNameAsString(), counter++);
            }
        }

        DisjointSet disjointSet = new DisjointSet(counter);
        for (MethodDeclaration method : methods) {
            int methodIndex = methodIndexes.get(method);
            method.findAll(NameExpr.class).forEach(nameExpr -> {
                Integer fieldIndex = fieldIndexes.get(nameExpr.getNameAsString());
                if (fieldIndex != null) {
                    disjointSet.union(methodIndex, fieldIndex);
                }
            });
        }

        Set<Integer> components = new HashSet<>();
        for (MethodDeclaration method : methods) {
            components.add(disjointSet.find(methodIndexes.get(method)));
        }
        return components.size();
    }

    /**
     * Union-find structure used to compute connected components between methods and fields.
     *
     * @param parent parent array for the disjoint-set forest
     */
    private record DisjointSet(int[] parent) {
            /**
             * Creates a disjoint set with one singleton component per index.
             *
             * @param parent number of singleton elements to create
             */
            private DisjointSet(int parent) {
                this(IntStream.range(0, parent).toArray());
            }

            /**
             * Finds the representative of one element with path compression.
             *
             * @param index element index
             * @return representative index
             */
            private int find(int index) {
                if (parent[index] != index) {
                    parent[index] = find(parent[index]);
                }
                return parent[index];
            }

            /**
             * Merges the components containing the two elements.
             *
             * @param left left element index
             * @param right right element index
             */
            private void union(int left, int right) {
                int parentLeft = find(left);
                int parentRight = find(right);
                if (parentLeft != parentRight) {
                    parent[parentRight] = parentLeft;
                }
            }
        }
}
