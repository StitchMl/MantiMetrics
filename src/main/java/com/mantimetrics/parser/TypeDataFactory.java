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

final class TypeDataFactory {
    private static final Logger LOG = LoggerFactory.getLogger(TypeDataFactory.class);
    private final JavaCompilationUnitLoader loader = new JavaCompilationUnitLoader();

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
