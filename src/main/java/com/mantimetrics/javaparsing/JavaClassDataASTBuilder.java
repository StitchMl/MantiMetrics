package com.mantimetrics.javaparsing;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.mantimetrics.feature.MetricsCalculator;
import com.mantimetrics.datasetsetting.DatasetClassData;
import com.mantimetrics.utility.JavaTypeUtility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds class-level dataset rows from parsed Java source files.
 */
final class JavaClassDataASTBuilder {
    private static final Logger LOG = LoggerFactory.getLogger(JavaClassDataASTBuilder.class);
    private final CompilationUnitLoader loader = new CompilationUnitLoader();

    /**
     * Collects class rows from one parsed source file.
     *
     * @param sourceFile parsed source file
     * @param repo project name
     * @param tag release identifier
     * @param calculator metrics calculator
     * @return class rows extracted from the file
     */
    List<DatasetClassData> collect(
            ParsedFileRappresentation sourceFile,
            String repo,
            String tag,
            MetricsCalculator calculator
    ) {
        List<DatasetClassData> types = new ArrayList<>();
        loader.parse(sourceFile.source(), sourceFile.relativePath(), "CLASS")
                .ifPresent(unit -> collectTypes(unit, sourceFile, repo, tag, calculator, types));
        return types;
    }

    /**
     * Collects class rows from a parsed compilation unit and appends them to the sink list.
     *
     * @param unit parsed compilation unit
     * @param sourceFile parsed source file
     * @param repo project name
     * @param tag release identifier
     * @param calculator metrics calculator
     * @param sink output list receiving the collected rows
     */
    private void collectTypes(
            CompilationUnit unit,
            ParsedFileRappresentation sourceFile,
            String repo,
            String tag,
            MetricsCalculator calculator,
            List<DatasetClassData> sink
    ) {
        for (TypeDeclaration<?> type : JavaTypeUtility.supportedTypes(unit)) {
            type.getRange().ifPresent(range -> {
                try {
                    sink.add(new DatasetClassData.Builder()
                            .projectName(repo)
                            .path('/' + sourceFile.relativePath() + '/')
                            .className(JavaTypeUtility.qualifiedName(type))
                            .releaseId(tag)
                            .metrics(calculator.computeAll(type))
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
