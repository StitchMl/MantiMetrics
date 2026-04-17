package com.mantimetrics.parser;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.mantimetrics.metrics.MetricsCalculator;
import com.mantimetrics.model.ClassData;
import com.mantimetrics.util.JavaTypeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds class-level dataset rows from parsed Java source files.
 */
final class TypeDataFactory {
    private static final Logger LOG = LoggerFactory.getLogger(TypeDataFactory.class);
    private final JavaCompilationUnitLoader loader = new JavaCompilationUnitLoader();

    /**
     * Collects class rows from one parsed source file.
     *
     * @param cloneCacheKey clone-cache key prepared for the release
     * @param sourceFile parsed source file
     * @param repo project name
     * @param tag release identifier
     * @param calculator metrics calculator
     * @return class rows extracted from the file
     */
    List<ClassData> collect(
            String cloneCacheKey,
            ParsedSourceFile sourceFile,
            String repo,
            String tag,
            MetricsCalculator calculator
    ) {
        List<ClassData> types = new ArrayList<>();
        loader.parse(sourceFile.source(), sourceFile.relativePath(), "CLASS")
                .ifPresent(unit -> collectTypes(unit, cloneCacheKey, sourceFile, repo, tag, calculator, types));
        return types;
    }

    /**
     * Collects class rows from a parsed compilation unit and appends them to the sink list.
     *
     * @param unit parsed compilation unit
     * @param cloneCacheKey clone-cache key prepared for the release
     * @param sourceFile parsed source file
     * @param repo project name
     * @param tag release identifier
     * @param calculator metrics calculator
     * @param sink output list receiving the collected rows
     */
    private void collectTypes(
            CompilationUnit unit,
            String cloneCacheKey,
            ParsedSourceFile sourceFile,
            String repo,
            String tag,
            MetricsCalculator calculator,
            List<ClassData> sink
    ) {
        for (TypeDeclaration<?> type : JavaTypeUtils.supportedTypes(unit)) {
            type.getRange().ifPresent(range -> {
                try {
                    sink.add(new ClassData.Builder()
                            .projectName(repo)
                            .path('/' + sourceFile.relativePath() + '/')
                            .className(JavaTypeUtils.qualifiedName(type))
                            .releaseId(tag)
                            .metrics(calculator.computeAll(type, cloneCacheKey, sourceFile.relativePath()))
                            .commitHashes(sourceFile.jiraKeys())
                            .buggy(false)
                            .startLine(range.begin.line)
                            .endLine(range.end.line)
                            .build());
                } catch (Exception exception) {
                    LOG.warn("[CLASS] Failed to compute metrics for {}: {}",
                            sourceFile.relativePath(), exception.getMessage());
                }
            });
        }
    }
}
