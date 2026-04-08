package com.mantimetrics.analysis;

import com.mantimetrics.Granularity;
import com.mantimetrics.ProjectContext;
import com.mantimetrics.clone.CloneDetector;
import com.mantimetrics.csv.CsvWriteException;
import com.mantimetrics.git.ReleaseCommitData;
import com.mantimetrics.model.DatasetRow;
import com.mantimetrics.parser.CodeParser;
import com.mantimetrics.parser.CodeParserException;
import com.mantimetrics.parser.SourceScanResult;
import com.mantimetrics.release.ReleaseProcessingException;
import net.sourceforge.pmd.reporting.Report;
import net.sourceforge.pmd.reporting.RuleViolation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public final class ReleaseExecutionService {
    private static final Logger LOG = LoggerFactory.getLogger(ReleaseExecutionService.class);

    private final CodeParser codeParser;
    private final ReleaseDatasetCollector datasetCollector;

    public ReleaseExecutionService(CodeParser codeParser, ReleaseDatasetCollector datasetCollector) {
        this.codeParser = codeParser;
        this.datasetCollector = datasetCollector;
    }

    void processRelease(@NotNull String tag, @Nullable String prevTag, @NotNull List<ProjectContext> contexts) {
        if (contexts.isEmpty()) {
            return;
        }

        ProjectContext baseContext = contexts.get(0);
        LOG.info("Processing {}@{} (prev={}) [{}]", baseContext.repo(), tag, prevTag,
                contexts.stream().map(ProjectContext::granularity).toList());

        try {
            ReleaseCommitData commitData = baseContext.git().buildReleaseCommitData(
                    baseContext.owner(), baseContext.repo(), prevTag, tag);
            LOG.info("{}@{} - {} files touched", baseContext.repo(), tag, commitData.touchMap().size());
            LOG.info("{}@{} - {} files linked to bug-fix issue keys in range",
                    baseContext.repo(), tag, commitData.fileToIssueKeys().size());

            Set<String> touchedFiles = commitData.touchMap().keySet();
            if (touchedFiles.isEmpty()) {
                LOG.info("{}@{} - no touched Java sources in release range, skipping analysis", baseContext.repo(), tag);
                return;
            }

            SourceScanResult releaseSources = codeParser.loadReleaseSources(baseContext.owner(), baseContext.repo(), tag);
            String cloneCacheKey = CloneDetector.prepareCloneMap(releaseSources);
            SourceScanResult analyzedSources = releaseSources.filterTo(touchedFiles);
            Report report = baseContext.pmd().analyze(analyzedSources);
            List<RuleViolation> violations = report.getViolations();
            if (violations.isEmpty()) {
                LOG.warn("{}@{} - no PMD violation found on touched sources",
                        baseContext.repo(), tag);
            }

            PreparedRelease prepared = new PreparedRelease(analyzedSources, cloneCacheKey, violations, commitData);
            try {
                for (ProjectContext context : contexts) {
                    processPreparedRelease(tag, context, prepared);
                }
            } finally {
                CloneDetector.evict(cloneCacheKey);
            }
        } catch (CodeParserException exception) {
            LOG.error("{}@{} - release skipped: {}", baseContext.repo(), tag, exception.getMessage());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ReleaseProcessingException("Thread interrupted during release processing " + tag, exception);
        } catch (IOException exception) {
            throw new ReleaseProcessingException("I/O error during release processing " + tag, exception);
        }
    }

    private void processPreparedRelease(String tag, ProjectContext context, PreparedRelease prepared) {
        LOG.info("Processing {}@{} [{}]", context.repo(), tag, context.granularity());
        try {
            List<? extends DatasetRow> rows = context.granularity() == Granularity.CLASS
                    ? datasetCollector.collectClassRows(prepared.analyzedSources(), prepared.analyzedSources(), context.repo(), tag,
                    prepared.cloneCacheKey(),
                    prepared.commitData().touchMap(), prepared.commitData().fileToIssueKeys(),
                    context.prevData(), prepared.violations(), context.bugKeys())
                    : datasetCollector.collectMethodRows(prepared.analyzedSources(), prepared.analyzedSources(), context.repo(), tag,
                    prepared.cloneCacheKey(),
                    prepared.commitData().touchMap(), prepared.commitData().fileToIssueKeys(),
                    context.prevData(), prepared.violations(), context.bugKeys());

            LOG.info("{}@{} - finalRows={} (violationsThisRelease={})",
                    context.repo(), tag, rows.size(), prepared.violations().size());

            updatePreviousData(context.prevData(), rows);
            context.csvOut().append(context.writer(), rows);

            long buggyCount = rows.stream().filter(DatasetRow::isBuggy).count();
            LOG.info("{}@{} - saved {} rows ({} buggy)", context.repo(), tag, rows.size(), buggyCount);
        } catch (CsvWriteException exception) {
            LOG.error("{}@{} - release skipped: {}", context.repo(), tag, exception.getMessage());
        }
    }

    private void updatePreviousData(Map<String, DatasetRow> prevData, List<? extends DatasetRow> rows) {
        prevData.clear();
        prevData.putAll(rows.stream()
                .collect(Collectors.toMap(
                        DatasetRow::getUniqueKey,
                        row -> row,
                        (left, right) -> right
                )));
    }

    private record PreparedRelease(
            SourceScanResult analyzedSources,
            String cloneCacheKey,
            List<RuleViolation> violations,
            ReleaseCommitData commitData
    ) {
    }
}
