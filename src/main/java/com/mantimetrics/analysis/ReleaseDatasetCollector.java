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

    /**
     * Creates a collector able to parse release sources and compute metrics before enrichment.
     *
     * @param parser parser used to extract class and method rows
     * @param calculator metrics calculator applied during parsing
     */
    public ReleaseDatasetCollector(CodeParser parser, MetricsCalculator calculator) {
        this.parser = parser;
        this.calculator = calculator;
        this.rowEnricher = new DatasetRowEnricher();
    }

    /**
     * Collects enriched method-level dataset rows for a prepared release.
     *
     * @param request prepared release request
     * @return enriched method rows keyed by the latest occurrence of each unique identifier
     */
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

    /**
     * Collects enriched class-level dataset rows for a prepared release.
     *
     * @param request prepared release request
     * @return enriched class rows keyed by the latest occurrence of each unique identifier
     */
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

    /**
     * Deduplicates rows by unique key, keeping the last value produced by the parser.
     *
     * @param rows parsed rows to deduplicate
     * @param <R> dataset row subtype
     * @return deduplicated rows preserving the last occurrence for each key
     */
    private <R extends DatasetRow> List<R> uniqueByKey(List<R> rows) {
        return rows.stream()
                .collect(Collectors.toMap(DatasetRow::getUniqueKey, row -> row, (left, right) -> right))
                .values()
                .stream()
                .toList();
    }
}
