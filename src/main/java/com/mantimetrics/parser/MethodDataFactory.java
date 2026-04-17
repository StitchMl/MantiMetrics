package com.mantimetrics.parser;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.mantimetrics.metrics.MetricsCalculator;
import com.mantimetrics.model.MethodData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds method-level dataset rows from parsed Java source files.
 */
final class MethodDataFactory {
    private static final Logger LOG = LoggerFactory.getLogger(MethodDataFactory.class);
    private final JavaCompilationUnitLoader loader = new JavaCompilationUnitLoader();

    /**
     * Collects method rows from one parsed source file.
     *
     * @param cloneCacheKey clone-cache key prepared for the release
     * @param sourceFile parsed source file
     * @param repo project name
     * @param tag release identifier
     * @param calculator metrics calculator
     * @return method rows extracted from the file
     */
    List<MethodData> collect(
            String cloneCacheKey,
            ParsedSourceFile sourceFile,
            String repo,
            String tag,
            MetricsCalculator calculator
    ) {
        List<MethodData> methods = new ArrayList<>();
        loader.parse(sourceFile.source(), sourceFile.relativePath(), "METHOD")
                .ifPresent(unit -> collectMethods(unit, cloneCacheKey, sourceFile, repo, tag, calculator, methods));
        return methods;
    }

    /**
     * Collects method rows from a parsed compilation unit and appends them to the sink list.
     *
     * @param unit parsed compilation unit
     * @param cloneCacheKey clone-cache key prepared for the release
     * @param sourceFile parsed source file
     * @param repo project name
     * @param tag release identifier
     * @param calculator metrics calculator
     * @param sink output list receiving the collected rows
     */
    private void collectMethods(
            CompilationUnit unit,
            String cloneCacheKey,
            ParsedSourceFile sourceFile,
            String repo,
            String tag,
            MetricsCalculator calculator,
            List<MethodData> sink
    ) {
        unit.findAll(MethodDeclaration.class).forEach(method ->
                method.getRange().ifPresent(range -> {
                    try {
                        sink.add(new MethodData.Builder()
                                .projectName(repo)
                                .path('/' + sourceFile.relativePath() + '/')
                                .methodSignature(method.getDeclarationAsString(true, true, true))
                                .releaseId(tag)
                                .metrics(calculator.computeAll(method, cloneCacheKey, sourceFile.relativePath()))
                                .commitHashes(sourceFile.jiraKeys())
                                .buggy(false)
                                .startLine(range.begin.line)
                                .endLine(range.end.line)
                                .build());
                    } catch (IOException exception) {
                        LOG.warn("[METHOD] Failed to compute metrics for {}: {}",
                                sourceFile.relativePath(), exception.getMessage());
                    }
                }));
    }
}
