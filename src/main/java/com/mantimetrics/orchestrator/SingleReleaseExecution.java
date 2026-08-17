package com.mantimetrics.orchestrator;

import com.mantimetrics.datasetoutput.CSVException;
import com.mantimetrics.datasetsetting.DatasetRow;
import com.mantimetrics.javaparsing.JavaSourceParser;
import com.mantimetrics.javaparsing.JavaParsingException;
import com.mantimetrics.javaparsing.ScanResult;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Executes the expensive work for a single release: source download and dataset row generation.
 * Code-smell counts come exclusively from the SonarCloud index built in {@link Orchestrator}.
 */
public final class SingleReleaseExecution {
    private static final Logger LOG = LoggerFactory.getLogger(SingleReleaseExecution.class);

    private final JavaSourceParser codeParser;
    private final ReleaseToDataset datasetCollector;

    /**
     * Creates a release executor with the parsing and dataset-collection services it coordinates.
     *
     * @param codeParser parser service responsible for loading release sources
     * @param datasetCollector collector used to build dataset rows for each granularity
     */
    public SingleReleaseExecution(JavaSourceParser codeParser, ReleaseToDataset datasetCollector) {
        this.codeParser = codeParser;
        this.datasetCollector = datasetCollector;
    }

    /**
     * Downloads and parses one release's sources into raw class rows (product metrics only).
     * Flag-independent: cache once, enrich per variant.
     *
     * @param owner repository owner
     * @param repo repository name
     * @param tag release tag
     * @return raw parsed class rows
     * @throws JavaParsingException when the release sources cannot be loaded or parsed
     */
    public List<com.mantimetrics.datasetsetting.DatasetClassData> parseRelease(String owner, String repo, String tag)
            throws JavaParsingException {
        ScanResult sources = codeParser.loadReleaseSources(owner, repo, tag);
        return datasetCollector.parse(sources, repo, tag);
    }

    /**
     * Enriches cached raw class rows for one release and variant (no network calls).
     *
     * @param rawRows raw parsed class rows
     * @param request per-release request for the current variant
     * @return enriched rows
     */
    public List<com.mantimetrics.datasetsetting.DatasetClassData> enrich(
            List<com.mantimetrics.datasetsetting.DatasetClassData> rawRows, ReleaseToDatasetRequest request) {
        return datasetCollector.enrich(rawRows, request);
    }

    /**
     * Processes one release snapshot. The snapshot already carries commit history so the expensive GitHub
     * history walk is not repeated for each dataset granularity.
     *
     * @param snapshot release snapshot to execute
     * @param contexts open project contexts, one for each requested granularity
     */
    void processRelease(@NotNull ReleaseSnapshot snapshot, @NotNull List<SharedStatus> contexts) {
        if (contexts.isEmpty()) {
            return;
        }

        SharedStatus baseContext = contexts.get(0);
        String tag = snapshot.tag();
        String prevTag = snapshot.previousTag();
        LOG.info("Processing {}@{} (prev={})", baseContext.repo(), tag, prevTag);

        try {
            LOG.info("{}@{} - {} files touched", baseContext.repo(), tag, snapshot.commitData().touchMap().size());
            LOG.info("{}@{} - {} files linked to bug-fix issue keys in range",
                    baseContext.repo(), tag, snapshot.commitData().fileToIssueKeys().size());

            ScanResult releaseSources = codeParser.loadReleaseSources(baseContext.owner(), baseContext.repo(), tag);

            PreparedRelease prepared = new PreparedRelease(releaseSources, snapshot.commitData());
            for (SharedStatus context : contexts) {
                processPreparedRelease(tag, context, prepared);
            }
        } catch (JavaParsingException exception) {
            LOG.error("{}@{} - release skipped: {}", baseContext.repo(), tag, exception.getMessage());
        }
    }

    /**
     * Delegates row collection to the correct granularity-specific collector while reusing the same prepared release.
     *
     * @param tag release tag currently being processed
     * @param context output context for the current granularity
     * @param prepared reusable prepared release state
     */
    private void processPreparedRelease(String tag, SharedStatus context, PreparedRelease prepared) {
        LOG.info("Processing {}@{}", context.repo(), tag);
        try {
            Map<String, Integer> sonarSmells =
                    context.sonarSmellsByTag().getOrDefault(tag, Map.of());
            ReleaseToDatasetRequest request = new ReleaseToDatasetRequest(
                    prepared.releaseSources(),
                    context.repo(),
                    tag,
                    prepared.commitData(),
                    context.prevData(),
                    context.historyStore(),
                    context.labelIndex(),
                    sonarSmells,
                    context.excludeChurnZero(),
                    context.ticketsByKey(),
                    context.openTicketsByRelease().getOrDefault(tag, 0),
                    context.ticketTouchedPaths(),
                    context.orderedTicketsByRelease().getOrDefault(tag, java.util.List.of())
            );
            List<? extends DatasetRow> rows = datasetCollector.collectClassRows(request);

            LOG.info("{}@{} - finalRows={}", context.repo(), tag, rows.size());

            updatePreviousData(context.prevData(), rows);
            context.csvOut().append(context.writer(), rows);

            long buggyCount = rows.stream().filter(DatasetRow::isBuggy).count();
            LOG.info("{}@{} - saved {} rows ({} buggy)", context.repo(), tag, rows.size(), buggyCount);
        } catch (CSVException exception) {
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
     * @param commitData commit-range metadata for the release
     */
    private record PreparedRelease(
            ScanResult releaseSources,
            com.mantimetrics.git.GitReleaseSnapshot commitData
    ) {
    }
}
