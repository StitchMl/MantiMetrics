package com.mantimetrics.analysis;

import com.mantimetrics.clone.CloneDetector;
import com.mantimetrics.csv.CsvWriteException;
import com.mantimetrics.model.DatasetRow;
import com.mantimetrics.parser.CodeParser;
import com.mantimetrics.parser.CodeParserException;
import com.mantimetrics.parser.SourceScanResult;
import com.mantimetrics.release.ReleaseProcessingException;
import net.sourceforge.pmd.reporting.Report;
import net.sourceforge.pmd.reporting.RuleViolation;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Executes the expensive work for a single release: source download, PMD scan and dataset row generation.
 */
public final class ReleaseExecutionService {
    private static final Logger LOG = LoggerFactory.getLogger(ReleaseExecutionService.class);

    private final CodeParser codeParser;
    private final ReleaseDatasetCollector datasetCollector;

    /**
     * Creates a release executor with the parsing and dataset-collection services it coordinates.
     *
     * @param codeParser parser service responsible for loading release sources
     * @param datasetCollector collector used to build dataset rows for each granularity
     */
    public ReleaseExecutionService(CodeParser codeParser, ReleaseDatasetCollector datasetCollector) {
        this.codeParser = codeParser;
        this.datasetCollector = datasetCollector;
    }

    /**
     * Processes one release snapshot. The snapshot already carries commit history so the expensive GitHub
     * history walk is not repeated for each dataset granularity.
     *
     * @param snapshot release snapshot to execute
     * @param contexts open project contexts, one for each requested granularity
     */
    void processRelease(@NotNull ReleaseSnapshot snapshot, @NotNull List<ProjectContext> contexts) {
        if (contexts.isEmpty()) {
            return;
        }

        ProjectContext baseContext = contexts.get(0);
        String tag = snapshot.tag();
        String prevTag = snapshot.previousTag();
        LOG.info("Processing {}@{} (prev={}) [{}]", baseContext.repo(), tag, prevTag,
                contexts.stream().map(ProjectContext::granularity).toList());

        try {
            LOG.info("{}@{} - {} files touched", baseContext.repo(), tag, snapshot.commitData().touchMap().size());
            LOG.info("{}@{} - {} files linked to bug-fix issue keys in range",
                    baseContext.repo(), tag, snapshot.commitData().fileToIssueKeys().size());

            SourceScanResult releaseSources = codeParser.loadReleaseSources(baseContext.owner(), baseContext.repo(), tag);
            String cloneCacheKey = CloneDetector.prepareCloneMap(releaseSources);
            Report report = baseContext.pmd().analyze(releaseSources);
            List<RuleViolation> violations = report.getViolations();
            if (violations.isEmpty()) {
                LOG.warn("{}@{} - no PMD violation found on release sources",
                        baseContext.repo(), tag);
            }

            PreparedRelease prepared = new PreparedRelease(releaseSources, cloneCacheKey, violations, snapshot.commitData());
            try {
                for (ProjectContext context : contexts) {
                    processPreparedRelease(tag, context, prepared);
                }
            } finally {
                CloneDetector.evict(cloneCacheKey);
            }
        } catch (CodeParserException exception) {
            LOG.error("{}@{} - release skipped: {}", baseContext.repo(), tag, exception.getMessage());
        } catch (IOException exception) {
            throw new ReleaseProcessingException("I/O error during release processing " + tag, exception);
        }
    }

    /**
     * Delegates row collection to the correct granularity-specific collector while reusing the same prepared release.
     *
     * @param tag release tag currently being processed
     * @param context output context for the current granularity
     * @param prepared reusable prepared release state
     */
    private void processPreparedRelease(String tag, ProjectContext context, PreparedRelease prepared) {
        LOG.info("Processing {}@{} [{}]", context.repo(), tag, context.granularity());
        try {
            ReleaseDatasetRequest request = new ReleaseDatasetRequest(
                    prepared.releaseSources(),
                    context.repo(),
                    tag,
                    prepared.cloneCacheKey(),
                    prepared.commitData(),
                    context.prevData(),
                    context.historyStore(),
                    prepared.violations(),
                    context.labelIndex()
            );
            List<? extends DatasetRow> rows = context.granularity() == Granularity.CLASS
                    ? datasetCollector.collectClassRows(request)
                    : datasetCollector.collectMethodRows(request);

            LOG.info("{}@{} - finalRows={} (violationsThisRelease={})",
                    context.repo(), tag, rows.size(), prepared.violations().size());

            updatePreviousData(context.prevData(), rows);
            context.csvOut().append(context.writer(), rows);

            long buggyCount = rows.stream().filter(DatasetRow::isBuggy).count();
            LOG.info("{}@{} - saved {} rows ({} buggy)", context.repo(), tag, rows.size(), buggyCount);
        } catch (CsvWriteException exception) {
            LOG.error("[Prepared] {}@{} - release skipped: {}", context.repo(), tag, exception.getMessage());
        }
    }

    /**
     * Replaces the previous-row cache with the rows produced for the current release.
     *
     * @param prevData cache of previous rows to overwrite
     * @param rows rows produced for the current release
     */
    private void updatePreviousData(Map<String, DatasetRow> prevData, List<? extends DatasetRow> rows) {
        prevData.clear();
        prevData.putAll(rows.stream()
                .collect(Collectors.toMap(
                        DatasetRow::getUniqueKey,
                        row -> row,
                        (left, right) -> right
                )));
    }

    /**
     * Prepared release state shared across granularity-specific dataset collectors.
     *
     * @param releaseSources extracted source tree for the release
     * @param cloneCacheKey key used to access the cached clone map
     * @param violations PMD rule violations found in the release
     * @param commitData commit-range metadata for the release
     */
    private record PreparedRelease(
            SourceScanResult releaseSources,
            String cloneCacheKey,
            List<RuleViolation> violations,
            com.mantimetrics.git.ReleaseCommitData commitData
    ) {
    }
}
