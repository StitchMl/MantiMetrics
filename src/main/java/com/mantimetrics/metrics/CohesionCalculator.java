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

public final class CohesionCalculator {

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

    private static final class DisjointSet {
        private final int[] parent;

        private DisjointSet(int size) {
            this.parent = IntStream.range(0, size).toArray();
        }

        private int find(int index) {
            if (parent[index] != index) {
                parent[index] = find(parent[index]);
            }
            return parent[index];
        }

        private void union(int left, int right) {
            int parentLeft = find(left);
            int parentRight = find(right);
            if (parentLeft != parentRight) {
                parent[parentRight] = parentLeft;
            }
        }
    }
}
