package com.mantimetrics.analysis;

import com.mantimetrics.jira.JiraClient;
import com.mantimetrics.metrics.MetricsCalculator;
import com.mantimetrics.model.ClassData;
import com.mantimetrics.model.MethodData;
import com.mantimetrics.model.DatasetRow;
import com.mantimetrics.parser.CodeParser;
import com.mantimetrics.parser.SourceScanResult;
import net.sourceforge.pmd.reporting.RuleViolation;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class ReleaseDatasetCollector {
    private final CodeParser parser;
    private final MetricsCalculator calculator;
    private final DatasetRowEnricher rowEnricher;

    public ReleaseDatasetCollector(CodeParser parser, MetricsCalculator calculator, JiraClient jiraClient) {
        this.parser = parser;
        this.calculator = calculator;
        this.rowEnricher = new DatasetRowEnricher(jiraClient);
    }

    public List<MethodData> collectMethodRows(
            SourceScanResult releaseSources,
            SourceScanResult analyzedSources,
            String repo,
            String tag,
            String cloneCacheKey,
            Map<String, List<String>> touchMap,
            Map<String, List<String>> fileToKeys,
            Map<String, DatasetRow> prevData,
            List<RuleViolation> violations,
            List<String> bugKeys
    ) {
        List<MethodData> methods = parser.parseMethods(releaseSources, analyzedSources, repo, tag, cloneCacheKey, calculator, fileToKeys);
        return rowEnricher.enrichMethods(uniqueByKey(methods), touchMap, fileToKeys, prevData,
                ReleaseViolationIndex.from(violations), bugKeys);
    }

    public List<ClassData> collectClassRows(
            SourceScanResult releaseSources,
            SourceScanResult analyzedSources,
            String repo,
            String tag,
            String cloneCacheKey,
            Map<String, List<String>> touchMap,
            Map<String, List<String>> fileToKeys,
            Map<String, DatasetRow> prevData,
            List<RuleViolation> violations,
            List<String> bugKeys
    ) {
        List<ClassData> classes = parser.parseClasses(releaseSources, analyzedSources, repo, tag, cloneCacheKey, calculator, fileToKeys);
        return rowEnricher.enrichClasses(uniqueByKey(classes), touchMap, fileToKeys, prevData,
                ReleaseViolationIndex.from(violations), bugKeys);
    }

    private <R extends DatasetRow> List<R> uniqueByKey(List<R> rows) {
        return rows.stream()
                .collect(Collectors.toMap(DatasetRow::getUniqueKey, row -> row, (left, right) -> right))
                .values()
                .stream()
                .toList();
    }
}
