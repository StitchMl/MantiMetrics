package com.mantimetrics.analysis;

import com.mantimetrics.metrics.MetricsCalculator;
import com.mantimetrics.model.ClassData;
import com.mantimetrics.model.MethodData;
import com.mantimetrics.model.DatasetRow;
import com.mantimetrics.parser.CodeParser;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Converts one prepared release into immutable dataset rows.
 */
public final class ReleaseDatasetCollector {
    private final CodeParser parser;
    private final MetricsCalculator calculator;
    private final DatasetRowEnricher rowEnricher;

    public ReleaseDatasetCollector(CodeParser parser, MetricsCalculator calculator) {
        this.parser = parser;
        this.calculator = calculator;
        this.rowEnricher = new DatasetRowEnricher();
    }

    public List<MethodData> collectMethodRows(ReleaseDatasetRequest request) {
        List<MethodData> methods = parser.parseMethods(
                request.releaseSources(),
                request.releaseSources(),
                request.repo(),
                request.tag(),
                request.cloneCacheKey(),
                calculator,
                request.commitData().fileToIssueKeys()
        );
        return rowEnricher.enrichMethods(uniqueByKey(methods), request, ReleaseViolationIndex.from(request.violations()));
    }

    public List<ClassData> collectClassRows(ReleaseDatasetRequest request) {
        List<ClassData> classes = parser.parseClasses(
                request.releaseSources(),
                request.releaseSources(),
                request.repo(),
                request.tag(),
                request.cloneCacheKey(),
                calculator,
                request.commitData().fileToIssueKeys()
        );
        return rowEnricher.enrichClasses(uniqueByKey(classes), request, ReleaseViolationIndex.from(request.violations()));
    }

    private <R extends DatasetRow> List<R> uniqueByKey(List<R> rows) {
        return rows.stream()
                .collect(Collectors.toMap(DatasetRow::getUniqueKey, row -> row, (left, right) -> right))
                .values()
                .stream()
                .toList();
    }
}
