package com.mantimetrics.orchestrator;

import com.mantimetrics.feature.MetricsCalculator;
import com.mantimetrics.datasetSetting.DatasetClassData;
import com.mantimetrics.datasetSetting.DatasetRow;
import com.mantimetrics.javaParsing.JavaSourceParser;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Converts one prepared release into immutable dataset rows.
 */
public final class ReleaseToDataset {
    private final JavaSourceParser parser;
    private final MetricsCalculator calculator;
    private final DatasetRowEnricher rowEnricher;

    /**
     * Creates a collector able to parse release sources and compute metrics before enrichment.
     *
     * @param parser parser used to extract class and method rows
     * @param calculator metrics calculator applied during parsing
     */
    public ReleaseToDataset(JavaSourceParser parser, MetricsCalculator calculator) {
        this.parser = parser;
        this.calculator = calculator;
        this.rowEnricher = new DatasetRowEnricher();
    }

    /**
     * Collects enriched class-level dataset rows for a prepared release.
     *
     * @param request prepared release request
     * @return enriched class rows keyed by the latest occurrence of each unique identifier
     */
    public List<DatasetClassData> collectClassRows(ReleaseToDatasetRequest request) {
        List<DatasetClassData> classes = parser.parseClasses(
                request.releaseSources(),
                request.releaseSources(),
                request.repo(),
                request.tag(),
                calculator,
                request.commitData().fileToIssueKeys()
        );
        return rowEnricher.enrichClasses(uniqueByKey(classes), request);
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
