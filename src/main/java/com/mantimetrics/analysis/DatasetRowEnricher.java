package com.mantimetrics.analysis;

import com.mantimetrics.history.RowHistoryState;
import com.mantimetrics.metrics.MethodMetrics;
import com.mantimetrics.model.ClassData;
import com.mantimetrics.model.DatasetRow;
import com.mantimetrics.model.MethodData;
import com.mantimetrics.util.AnalysisPathUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;
import java.util.function.ToIntFunction;

/**
 * Applies release-local Git history, cumulative history and historical bug labels to parsed rows.
 */
final class DatasetRowEnricher {
 /**
 * Enriches method-level rows with release-local and cumulative history features.
 *
 * @param rows parsed method rows for the current release
 * @param request immutable release request carrying history and labels
 * @return enriched method rows ready for serialization
 */
 List<MethodData> enrichMethods(
 List<MethodData> rows,
 ReleaseDatasetRequest request
 ) {
 List<MethodData> result = new ArrayList<>();
 for (MethodData row : rows) {
 String relativePath = normalizedPath(row);
 List<String> commits = request.commitData().touchesFor(relativePath);
 int currentCodeSmells = codeSmellsForRow(row, request.sonarSmellsByFile());
 RowHistoryState historyState = updateHistory(
 row.getUniqueKey(), relativePath, request, row.getMetrics(), currentCodeSmells);
 MethodData previous = request.previousRows().get(row.getUniqueKey()) instanceof MethodData method ? method : null;
 result.add(row.toBuilder()
 .commitHashes(commits)
 .codeSmells(currentCodeSmells)
 .issueTouches(request.commitData().issueTouchesFor(relativePath).size())
 .totalIssueTouches(historyState.totalIssueTouches())
 .touches(commits.size())
 .totalTouches(historyState.totalTouches())
 .authors(distinctCount(request.commitData().authorsFor(relativePath)))
 .totalAuthors(historyState.totalAuthors())
 .addedLines(request.commitData().additionsFor(relativePath))
 .deletedLines(request.commitData().deletionsFor(relativePath))
 .churn(request.commitData().churnFor(relativePath))
 .totalChurn(historyState.totalChurn())
 .prevCodeSmells(previous != null ? previous.getCodeSmells() : 0)
 .ageInReleases(historyState.ageInReleases())
 .buggy(isBuggyRow(request.tag(), relativePath, request))
 .prevBuggy(previous != null && previous.isBuggy())
 .maxLoc(historyState.maxLoc())
 .maxCyclomatic(historyState.maxCyclomatic())
 .maxCognitive(historyState.maxCognitive())
 .maxNSmells(historyState.maxNSmells())
 .maxStmtCount(historyState.maxStmtCount())
 .maxDistinctOperators(historyState.maxDistinctOperators())
 .maxDistinctOperands(historyState.maxDistinctOperands())
 .maxTotalOperators(historyState.maxTotalOperators())
 .maxTotalOperands(historyState.maxTotalOperands())
 .maxVocabulary(historyState.maxVocabulary())
 .maxLength(historyState.maxLength())
 .maxVolume(historyState.maxVolume())
 .maxDifficulty(historyState.maxDifficulty())
 .maxEffort(historyState.maxEffort())
 .maxNestingDepth(historyState.maxNestingDepth())
 .everLongMethod(historyState.everLongMethod())
 .everGodClass(historyState.everGodClass())
 .everFeatureEnvy(historyState.everFeatureEnvy())
 .everDuplicatedCode(historyState.everDuplicatedCode())
 .maxCodeSmells(historyState.maxCodeSmells())
 .maxSmellDensity(historyState.maxSmellDensity())
 .build());
 }
 return result;
 }

 /**
 * Enriches class-level rows with release-local and cumulative history features.
 *
 * @param rows parsed class rows for the current release
 * @param request immutable release request carrying history and labels
 * @return enriched class rows ready for serialization
 */
 List<ClassData> enrichClasses(
 List<ClassData> rows,
 ReleaseDatasetRequest request
 ) {
 List<ClassData> result = new ArrayList<>();
 for (ClassData row : rows) {
 String relativePath = normalizedPath(row);
 List<String> commits = request.commitData().touchesFor(relativePath);
 int currentCodeSmells = codeSmellsForRow(row, request.sonarSmellsByFile());
 RowHistoryState historyState = updateHistory(
 row.getUniqueKey(), relativePath, request, row.getMetrics(), currentCodeSmells);
 ClassData previous = request.previousRows().get(row.getUniqueKey()) instanceof ClassData type ? type : null;
 result.add(row.toBuilder()
 .commitHashes(commits)
 .codeSmells(currentCodeSmells)
 .issueTouches(request.commitData().issueTouchesFor(relativePath).size())
 .totalIssueTouches(historyState.totalIssueTouches())
 .touches(commits.size())
 .totalTouches(historyState.totalTouches())
 .authors(distinctCount(request.commitData().authorsFor(relativePath)))
 .totalAuthors(historyState.totalAuthors())
 .addedLines(request.commitData().additionsFor(relativePath))
 .deletedLines(request.commitData().deletionsFor(relativePath))
 .churn(request.commitData().churnFor(relativePath))
 .totalChurn(historyState.totalChurn())
 .prevCodeSmells(previous != null ? previous.getCodeSmells() : 0)
 .ageInReleases(historyState.ageInReleases())
 .buggy(isBuggyRow(request.tag(), relativePath, request))
 .prevBuggy(previous != null && previous.isBuggy())
 .maxLoc(historyState.maxLoc())
 .maxCyclomatic(historyState.maxCyclomatic())
 .maxCognitive(historyState.maxCognitive())
 .maxNSmells(historyState.maxNSmells())
 .maxStmtCount(historyState.maxStmtCount())
 .maxDistinctOperators(historyState.maxDistinctOperators())
 .maxDistinctOperands(historyState.maxDistinctOperands())
 .maxTotalOperators(historyState.maxTotalOperators())
 .maxTotalOperands(historyState.maxTotalOperands())
 .maxVocabulary(historyState.maxVocabulary())
 .maxLength(historyState.maxLength())
 .maxVolume(historyState.maxVolume())
 .maxDifficulty(historyState.maxDifficulty())
 .maxEffort(historyState.maxEffort())
 .maxNestingDepth(historyState.maxNestingDepth())
 .everLongMethod(historyState.everLongMethod())
 .everGodClass(historyState.everGodClass())
 .everFeatureEnvy(historyState.everFeatureEnvy())
 .everDuplicatedCode(historyState.everDuplicatedCode())
 .maxCodeSmells(historyState.maxCodeSmells())
 .maxSmellDensity(historyState.maxSmellDensity())
 .build());
 }
 return result;
 }

 /**
 * Groups the per-release input values needed to compute the updated history state.
 *
 * @param relativePath normalized relative source path
 * @param request immutable release request
 * @param metrics current-release static metrics
 * @param currentCodeSmells PMD violation count for the current release
 * @param currentNSmells total smell count (binary + PMD) for the current release
 * @param currentSmellDensity smell density (NSmells / max(LOC,1)) for the current release
 */
 private record HistoryInput(
 String relativePath,
 ReleaseDatasetRequest request,
 MethodMetrics metrics,
 int currentCodeSmells,
 int currentNSmells,
 double currentSmellDensity
 ) {}

 /**
 * Updates the cumulative history state associated with a dataset row and returns the refreshed value.
 * Computes the current nsmells internally from the provided metrics and codeSmells count.
 *
 * @param uniqueKey stable dataset identifier for the row
 * @param relativePath normalized relative source path
 * @param request immutable release request carrying commit history and the mutable store
 * @param metrics current-release static metrics for the entity
 * @param currentCodeSmells PMD violation count for the entity in the current release
 * @return updated history state after processing the current release
 */
 private RowHistoryState updateHistory(
 String uniqueKey,
 String relativePath,
 ReleaseDatasetRequest request,
 MethodMetrics metrics,
 int currentCodeSmells
 ) {
 RowHistoryState previous = request.historyStore().get(uniqueKey);
 List<String> totalAuthors = mergeAuthors(previous, distinctAuthors(request.commitData().authorsFor(relativePath)));
 int currentNSmells = computeNSmells(metrics, currentCodeSmells);
 double currentSmellDensity = currentNSmells / (double) Math.max(metrics.getLoc(), 1);

 HistoryInput input = new HistoryInput(relativePath, request, metrics,
 currentCodeSmells, currentNSmells, currentSmellDensity);
 RowHistoryState updated = buildUpdatedHistory(previous, input, totalAuthors);
 request.historyStore().put(uniqueKey, updated);
 return updated;
 }

 /**
 * Merges the previous author list with the current release authors, preserving encounter order.
 */
 private List<String> mergeAuthors(RowHistoryState previous, List<String> currentAuthors) {
 List<String> totalAuthors = new ArrayList<>();
 if (previous != null) {
 totalAuthors.addAll(previous.authors());
 }
 currentAuthors.stream()
 .filter(author -> !totalAuthors.contains(author))
 .forEach(totalAuthors::add);
 return totalAuthors;
 }

 /**
 * Computes the total NSmells count from binary smell flags and PMD violations.
 */
 private int computeNSmells(MethodMetrics metrics, int currentCodeSmells) {
 int binarySmells = (metrics.isLongMethod() ? 1 : 0)
 + (metrics.isGodClass() ? 1 : 0)
 + (metrics.isFeatureEnvy() ? 1 : 0)
 + (metrics.isDuplicatedCode() ? 1 : 0);
 return currentCodeSmells + binarySmells;
 }

 /**
 * Builds the updated {@link RowHistoryState} by accumulating running counters and max values.
 * All prev-or-default lookups are delegated to {@link #prevInt}, {@link #prevDouble} and
 * {@link #prevBool} so this method contains no branching logic itself.
 */
 private RowHistoryState buildUpdatedHistory(
 RowHistoryState previous, HistoryInput input, List<String> totalAuthors) {
 MethodMetrics m = input.metrics();
 ReleaseDatasetRequest req = input.request();
 String path = input.relativePath();
 return new RowHistoryState(
 prevInt(previous, RowHistoryState::totalTouches) + req.commitData().touchesFor(path).size(),
 prevInt(previous, RowHistoryState::totalIssueTouches) + req.commitData().issueTouchesFor(path).size(),
 prevInt(previous, RowHistoryState::totalChurn) + req.commitData().churnFor(path),
 totalAuthors,
 prevInt(previous, RowHistoryState::ageInReleases) + 1,
 Math.max(prevInt(previous, RowHistoryState::maxLoc), m.getLoc()),
 Math.max(prevInt(previous, RowHistoryState::maxCyclomatic), m.getCyclomatic()),
 Math.max(prevInt(previous, RowHistoryState::maxCognitive), m.getCognitive()),
 Math.max(prevInt(previous, RowHistoryState::maxNSmells), input.currentNSmells()),
 Math.max(prevInt(previous, RowHistoryState::maxStmtCount), m.getStmtCount()),
 Math.max(prevInt(previous, RowHistoryState::maxDistinctOperators),m.getDistinctOperators()),
 Math.max(prevInt(previous, RowHistoryState::maxDistinctOperands), m.getDistinctOperands()),
 Math.max(prevInt(previous, RowHistoryState::maxTotalOperators), m.getTotalOperators()),
 Math.max(prevInt(previous, RowHistoryState::maxTotalOperands), m.getTotalOperands()),
 Math.max(prevDouble(previous, RowHistoryState::maxVocabulary), m.getVocabulary()),
 Math.max(prevDouble(previous, RowHistoryState::maxLength), m.getLength()),
 Math.max(prevDouble(previous, RowHistoryState::maxVolume), m.getVolume()),
 Math.max(prevDouble(previous, RowHistoryState::maxDifficulty), m.getDifficulty()),
 Math.max(prevDouble(previous, RowHistoryState::maxEffort), m.getEffort()),
 Math.max(prevInt(previous, RowHistoryState::maxNestingDepth), m.getMaxNestingDepth()),
 prevBool(previous, RowHistoryState::everLongMethod) || m.isLongMethod(),
 prevBool(previous, RowHistoryState::everGodClass) || m.isGodClass(),
 prevBool(previous, RowHistoryState::everFeatureEnvy) || m.isFeatureEnvy(),
 prevBool(previous, RowHistoryState::everDuplicatedCode)|| m.isDuplicatedCode(),
 Math.max(prevInt(previous, RowHistoryState::maxCodeSmells), input.currentCodeSmells()),
 Math.max(prevDouble(previous, RowHistoryState::maxSmellDensity), input.currentSmellDensity())
 );
 }

 /** Returns {@code fn(prev)} when {@code prev} is non-null, otherwise {@code 0}. */
 private static int prevInt(RowHistoryState prev, ToIntFunction<RowHistoryState> fn) {
 return prev != null ? fn.applyAsInt(prev) : 0;
 }

 /** Returns {@code fn(prev)} when {@code prev} is non-null, otherwise {@code 0.0}. */
 private static double prevDouble(RowHistoryState prev, ToDoubleFunction<RowHistoryState> fn) {
 return prev != null ? fn.applyAsDouble(prev) : 0.0;
 }

 /** Returns {@code fn(prev)} when {@code prev} is non-null, otherwise {@code false}. */
 private static boolean prevBool(RowHistoryState prev, Predicate<RowHistoryState> fn) {
 return prev != null && fn.test(prev);
 }

 /**
 * Returns the SonarCloud code-smell count for a row.
 * SonarCloud is the sole source of code-smell data; rows whose file is absent from the map
 * receive a count of zero (project not configured on SonarCloud, or file not analysed).
 *
 * @param row dataset row being enriched
 * @param sonarSmells SonarCloud file-level smell counts keyed by normalized path
 * @return code smell count for the row, or 0 when the file is not in the SonarCloud index
 */
 private int codeSmellsForRow(DatasetRow row, java.util.Map<String, Integer> sonarSmells) {
 return sonarSmells.getOrDefault(normalizedPath(row), 0);
 }

 /**
 * Reports whether the current row is historically labeled as buggy for the analyzed release.
 *
 * @param releaseId release identifier currently being processed
 * @param relativePath normalized relative source path
 * @param request immutable release request carrying the historical label index
 * @return {@code true} when the row belongs to a historically buggy file in the current release
 */
 private boolean isBuggyRow(String releaseId, String relativePath, ReleaseDatasetRequest request) {
 return request.labelIndex().isBuggy(releaseId, relativePath);
 }

 /**
 * Normalizes the row path into the canonical dataset representation.
 *
 * @param row dataset row whose path must be normalized
 * @return normalized relative path
 */
 private String normalizedPath(DatasetRow row) {
 return AnalysisPathUtils.normalizeDatasetPath(row.getPath());
 }

 /**
 * Removes duplicate authors while preserving their encounter order.
 *
 * @param authors authors collected from the current release history
 * @return distinct author list
 */
 private List<String> distinctAuthors(List<String> authors) {
 return authors.stream().distinct().toList();
 }

 /**
 * Counts the distinct authors touching a row.
 *
 * @param authors authors collected from the current release history
 * @return number of unique authors
 */
 private int distinctCount(List<String> authors) {
 return distinctAuthors(authors).size();
 }
}
