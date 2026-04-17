package com.mantimetrics.util;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.AnnotationDeclaration;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.CompactConstructorDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.InitializerDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.RecordDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.nodeTypes.NodeWithName;
import com.github.javaparser.ast.nodeTypes.NodeWithSimpleName;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Optional;

/**
 * Utility methods for navigating JavaParser type declarations and their direct members.
 */
public final class JavaTypeUtils {

    /**
     * Prevents instantiation of the static utility class.
     */
    private JavaTypeUtils() {
        throw new AssertionError("Do not instantiate JavaTypeUtils");
    }

    /**
     * Returns the supported type declarations found in a compilation unit ordered by source position.
     *
     * @param unit parsed compilation unit to inspect
     * @return immutable list of supported type declarations
     */
    public static List<TypeDeclaration<?>> supportedTypes(CompilationUnit unit) {
        List<TypeDeclaration<?>> types = new ArrayList<>();
        types.addAll(unit.findAll(ClassOrInterfaceDeclaration.class));
        types.addAll(unit.findAll(EnumDeclaration.class));
        types.addAll(unit.findAll(RecordDeclaration.class));
        types.addAll(unit.findAll(AnnotationDeclaration.class));
        types.sort(Comparator
                .comparingInt((TypeDeclaration<?> type) -> type.getRange()
                        .map(range -> range.begin.line)
                        .orElse(Integer.MAX_VALUE))
                .thenComparing(NodeWithSimpleName::getNameAsString));
        return List.copyOf(types);
    }

    /**
     * Computes the fully qualified name of a type declaration, including enclosing types when present.
     *
     * @param type type declaration to resolve
     * @return fully qualified type name
     */
    public static String qualifiedName(TypeDeclaration<?> type) {
        Deque<String> names = new ArrayDeque<>();
        names.addFirst(type.getNameAsString());

        Optional<Node> current = type.getParentNode();
        String packageName = "";
        while (current.isPresent()) {
            Node node = current.get();
            if (node instanceof TypeDeclaration<?> parentType) {
                names.addFirst(parentType.getNameAsString());
            } else if (node instanceof CompilationUnit unit) {
                packageName = unit.getPackageDeclaration()
                        .map(NodeWithName::getNameAsString)
                        .orElse("");
                break;
            }
            current = node.getParentNode();
        }

        String joined = String.join(".", names);
        return packageName.isBlank() ? joined : packageName + "." + joined;
    }

    /**
     * Returns the method declarations defined directly inside the given type.
     *
     * @param type type declaration to inspect
     * @return immutable list of direct methods
     */
    public static List<MethodDeclaration> directMethods(TypeDeclaration<?> type) {
        return type.getMembers().stream()
                .filter(MethodDeclaration.class::isInstance)
                .map(MethodDeclaration.class::cast)
                .toList();
    }

    /**
     * Returns the constructor declarations defined directly inside the given type.
     *
     * @param type type declaration to inspect
     * @return immutable list of direct constructors
     */
    public static List<ConstructorDeclaration> directConstructors(TypeDeclaration<?> type) {
        return type.getMembers().stream()
                .filter(ConstructorDeclaration.class::isInstance)
                .map(ConstructorDeclaration.class::cast)
                .toList();
    }

    /**
     * Returns the compact record constructors declared directly inside the given type.
     *
     * @param type type declaration to inspect
     * @return immutable list of direct compact constructors
     */
    public static List<CompactConstructorDeclaration> directCompactConstructors(TypeDeclaration<?> type) {
        return type.getMembers().stream()
                .filter(CompactConstructorDeclaration.class::isInstance)
                .map(CompactConstructorDeclaration.class::cast)
                .toList();
    }

    /**
     * Returns the initializer blocks declared directly inside the given type.
     *
     * @param type type declaration to inspect
     * @return immutable list of direct initializer blocks
     */
    public static List<InitializerDeclaration> directInitializers(TypeDeclaration<?> type) {
        return type.getMembers().stream()
                .filter(InitializerDeclaration.class::isInstance)
                .map(InitializerDeclaration.class::cast)
                .toList();
    }

    /**
     * Returns the field declarations declared directly inside the given type.
     *
     * @param type type declaration to inspect
     * @return immutable list of direct field declarations
     */
    public static List<FieldDeclaration> directFields(TypeDeclaration<?> type) {
        return type.getMembers().stream()
                .filter(FieldDeclaration.class::isInstance)
                .map(FieldDeclaration.class::cast)
                .toList();
    }

    /**
     * Returns the executable members declared directly inside the given type.
     *
     * @param type type declaration to inspect
     * @return immutable list containing methods, constructors, compact constructors and initializers
     */
    public static List<Node> directExecutableNodes(TypeDeclaration<?> type) {
        List<Node> nodes = new ArrayList<>();
        nodes.addAll(directMethods(type));
        nodes.addAll(directConstructors(type));
        nodes.addAll(directCompactConstructors(type));
        nodes.addAll(directInitializers(type));
        return List.copyOf(nodes);
    }

    /**
     * Returns every node that contributes executable or field-level metrics for the given type.
     *
     * @param type type declaration to inspect
     * @return immutable list of executable and field nodes
     */
    public static List<Node> directMetricNodes(TypeDeclaration<?> type) {
        List<Node> nodes = new ArrayList<>(directExecutableNodes(type));
        nodes.addAll(directFields(type));
        return List.copyOf(nodes);
    }

    /**
     * Finds the closest enclosing type declaration for a node.
     *
     * @param node node whose lexical parent type must be resolved
     * @return enclosing type, or an empty optional when the node is outside any type declaration
     */
    public static Optional<TypeDeclaration<?>> enclosingType(Node node) {
        Optional<Node> current = node.getParentNode();
        while (current.isPresent()) {
            Node value = current.get();
            if (value instanceof TypeDeclaration<?> type) {
                return Optional.of(type);
            }
            current = value.getParentNode();
        }
        return Optional.empty();
    }

    /**
     * Returns the body members declared directly inside the given type.
     *
     * @param type type declaration to inspect
     * @return immutable list of direct body members
     */
    @SuppressWarnings("unused")
    public static List<BodyDeclaration<?>> directMembers(TypeDeclaration<?> type) {
        return List.copyOf(type.getMembers());
    }
}
