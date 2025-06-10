package com.mantimetrics.metrics;

import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.NameExpr;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;

public class CohesionCalculator {

    /**
     * Calculates LCOM4 on class cls.
     * Returns the number of connected components (>=2 indicates lack of cohesion).
     */
    public int calculateLcom4(ClassOrInterfaceDeclaration cls) {
        // 1) I collect all methods and all fields
        List<MethodDeclaration> methods = cls.findAll(MethodDeclaration.class);
        List<FieldDeclaration>  fields  = cls.findAll(FieldDeclaration.class);

        // 2) Indexing: each method and each field has a unique index
        Map<String,Integer> idx = new HashMap<>();
        int counter = 0;
        for (MethodDeclaration m : methods) {
            idx.put("M:" + m.getNameAsString(), counter++);
        }
        for (FieldDeclaration f : fields) {
            // here I take the first variable name of the FieldDeclaration
            String fieldName = f.getVariables().get(0).getNameAsString();
            idx.put("F:" + fieldName, counter++);
        }

        // 3) Union-Find to connect methods and fields accessed
        DisjointSet ds = new DisjointSet(counter);
        for (MethodDeclaration m : methods) {
            String methodKey = "M:" + m.getNameAsString();
            int mi = idx.get(methodKey);
            // search for all NameExpr matching a field
            m.findAll(NameExpr.class).forEach(ne -> {
                String name = ne.getNameAsString();
                String fieldKey = "F:" + name;
                if (idx.containsKey(fieldKey)) {
                    int fi = idx.get(fieldKey);
                    ds.union(mi, fi);
                }
            });
        }

        // 4) I collect the components that contain at least one method
        Set<Integer> comps = new HashSet<>();
        for (MethodDeclaration m : methods) {
            int mi = idx.get("M:" + m.getNameAsString());
            comps.add(ds.find(mi));
        }

        return comps.size();
    }

    /**
     * Simple implementation of union-find (disjoint set).
     */
    private static class DisjointSet {
        private final int[] parent;

        DisjointSet(int n) {
            parent = IntStream.range(0, n).toArray();
        }

        int find(int x) {
            if (parent[x] != x) {
                parent[x] = find(parent[x]);
            }
            return parent[x];
        }

        void union(int a, int b) {
            int pa = find(a);
            int pb = find(b);
            if (pa != pb) {
                parent[pb] = pa;
            }
        }
    }
}