package com.mantimetrics.orchestrator;

import com.mantimetrics.feature.MetricsCalculator;
import com.mantimetrics.datasetsetting.DatasetClassData;
import com.mantimetrics.datasetsetting.DatasetRow;
import com.mantimetrics.javaparsing.JavaSourceParser;

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
     * Parses class rows from already-downloaded release sources (no enrichment). Flag-independent,
     * so the result can be cached once and enriched per dataset variant.
     *
     * @param sources scanned release sources
     * @param repo repository name
     * @param tag release identifier
     * @return raw class rows with product metrics only
     */
    public List<DatasetClassData> parse(com.mantimetrics.javaparsing.ScanResult sources, String repo, String tag) {
        return parser.parseClasses(sources, sources, repo, tag, calculator, java.util.Map.of());
    }

    /**
     * Enriches already-parsed class rows with process/history/labeling/TLP features and applies the
     * churn-zero filter. Contains no network calls, so it can run per variant from cached raw rows.
     *
     * @param rawRows raw parsed class rows for the release
     * @param request per-release request carrying commit data, labels, tickets and flags
     * @return enriched class rows
     */
    public List<DatasetClassData> enrich(List<DatasetClassData> rawRows, ReleaseToDatasetRequest request) {
        List<DatasetClassData> enriched = rowEnricher.enrichClasses(uniqueByKey(rawRows), request);
        if (request.excludeChurnZero()) {
            enriched = enriched.stream().filter(row -> row.getChurn() != 0).toList();
        }
        return enriched;
    }

    public List<DatasetClassData> collectClassRows(ReleaseToDatasetRequest request) {
        List<DatasetClassData> classes = parser.parseClasses(
                request.releaseSources(),
                request.releaseSources(),
                request.repo(),
                request.tag(),
                calculator,
                request.commitData().fileToIssueKeys()
        );
        List<DatasetClassData> enriched = rowEnricher.enrichClasses(uniqueByKey(classes), request);
        if (request.excludeChurnZero()) {
            enriched = enriched.stream().filter(row -> row.getChurn() != 0).toList();
        }
        return enriched;
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
