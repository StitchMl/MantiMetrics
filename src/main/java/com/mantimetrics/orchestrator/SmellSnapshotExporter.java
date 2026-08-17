package com.mantimetrics.orchestrator;

import com.mantimetrics.config.GitTokenLoader;
import com.mantimetrics.config.SonarTokenLoader;
import com.mantimetrics.git.GitConfig;
import com.mantimetrics.git.GitFacade;
import com.mantimetrics.smell.SonarClient;
import com.mantimetrics.smell.SonarException;
import com.mantimetrics.smell.SonarPreScanOrchestrator;
import com.mantimetrics.utility.ProgressBar;
import com.mantimetrics.utility.TmpDirCleaner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Standalone utility that (re)scans each release tag on SonarCloud and exports its file-level
 * code-smell counts to {@code output/smells/<tag>.tsv}. Unlike the full pipeline it does NOT touch
 * Jira, commit history or source parsing, so it only downloads the release ZIPs it needs - far faster
 * than a full dataset re-run. Each tag's smells are captured immediately after that tag is scanned
 * (while it is the current SonarCloud state), then a separate patch step injects them into the CSVs.
 *
 * <p>Run this AFTER disabling SonarCloud Automatic Analysis
 * ({@code https://sonarcloud.io/project/analysis_method?id=<key>} -> "Other CI tools" -> Save):
 * <pre>
 *   mvnw -q exec:java "-Dexec.mainClass=com.mantimetrics.orchestrator.SmellSnapshotExporter" ^
 *        "-Dexec.args=--repo-url=<a href="https://github.com/apache/avro">...</a> --sonar-key=StitchMl_avro"
 * </pre>
 *
 * <p>Options (all optional):
 * <ul>
 *   <li>{@code --repo-url=<url>} (default {@code https://github.com/apache/avro})</li>
 *   <li>{@code --sonar-key=<key>} (default {@code StitchMl_avro})</li>
 *   <li>{@code --tags-from=<csv>} dataset CSV whose distinct {@code ReleaseId} values are the tags to
 *       export (default {@code output/batch/avro_pct34_total_gh0_churn0.csv})</li>
 * </ul>
 */
public final class SmellSnapshotExporter {
    private static final Logger LOG = LoggerFactory.getLogger(SmellSnapshotExporter.class);

    private SmellSnapshotExporter() {
    }

    /**
     * Entry point: wires the Git and SonarCloud services and exports one smell snapshot per tag.
     *
     * @param args optional {@code --repo-url}, {@code --sonar-key}, {@code --tags-from} flags
     * @throws IOException    when tokens, the tags CSV, or the ZIP downloads cannot be read
     * @throws SonarException when the SonarCloud output directory cannot be prepared
     */
    public static void main(String[] args) throws IOException, SonarException {
        String repoUrl  = arg(args, "--repo-url",  "https://github.com/apache/avro");
        String sonarKey = arg(args, "--sonar-key", "StitchMl_avro");
        String tagsFrom = arg(args, "--tags-from", "output/batch/avro_pct34_total_gh0_churn0.csv");
        String tagsList = arg(args, "--tags", null);
        Path outDir = Paths.get("output", "smells");

        GitConfig config = new GitConfig(null, null, repoUrl, 100, null, sonarKey);
        List<String> tags = (tagsList != null && !tagsList.isBlank())
                ? explicitTags(tagsList)
                : readTags(Paths.get(tagsFrom));
        if (tags.isEmpty()) {
            LOG.warn("No release tags found in {} - nothing to export.", tagsFrom);
            return;
        }
        LOG.info("SmellSnapshotExporter: {}/{} sonarKey={} tags={}",
                config.owner(), config.name(), sonarKey, tags.size());

        String githubToken = new GitTokenLoader().load(MainApp.class);
        String sonarToken  = new SonarTokenLoader().load(MainApp.class);
        GitFacade gitService = new GitFacade(githubToken);
        try (SonarClient sonarClient = new SonarClient(sonarToken);
             ProgressBar bar = new ProgressBar("Sonar export", tags.size())) {
            SonarPreScanOrchestrator exporter =
                    new SonarPreScanOrchestrator(gitService, sonarClient, sonarToken);
            int n = exporter.exportSmellSnapshots(
                    config.owner(), config.name(), tags, sonarKey, outDir, bar);
            LOG.info("SmellSnapshotExporter: {} new snapshot(s) written to {}", n, outDir);
        } finally {
            TmpDirCleaner.cleanup(gitService.getTmp());
        }
    }

    /**
     * Parses an explicit comma-separated {@code --tags} value into a de-duplicated ordered list.
     *
     * @param csv comma-separated release tags (e.g. {@code release-1.0.0,release-1.5.0})
     * @return ordered list of non-blank tags
     */
    private static List<String> explicitTags(String csv) {
        Set<String> tags = new LinkedHashSet<>();
        for (String t : csv.split(",")) {
            String trimmed = t.trim();
            if (!trimmed.isEmpty()) {
                tags.add(trimmed);
            }
        }
        return new ArrayList<>(tags);
    }

    private static String arg(String[] args, String name, String def) {
        String prefix = name + "=";
        for (String a : args) {
            if (a.startsWith(prefix)) {
                return a.substring(prefix.length());
            }
        }
        return def;
    }

    /**
     * Reads the distinct {@code ReleaseId} values (release tags) from a dataset CSV.
     *
     * @param csv path to a dataset CSV that contains a {@code ReleaseId} column
     * @return sorted list of distinct release tags
     * @throws IOException when the file cannot be read or lacks a {@code ReleaseId} column
     */
    private static List<String> readTags(Path csv) throws IOException {
        List<String> lines = Files.readAllLines(csv, StandardCharsets.UTF_8);
        if (lines.isEmpty()) {
            return List.of();
        }
        String[] header = lines.get(0).split(",");
        int idx = -1;
        for (int i = 0; i < header.length; i++) {
            if ("ReleaseId".equals(header[i].trim())) {
                idx = i;
                break;
            }
        }
        if (idx < 0) {
            throw new IOException("ReleaseId column not found in " + csv);
        }
        Set<String> tags = new LinkedHashSet<>();
        for (int i = 1; i < lines.size(); i++) {
            String[] cols = lines.get(i).split(",");
            if (idx < cols.length) {
                tags.add(cols[idx].trim());
            }
        }
        List<String> out = new ArrayList<>(tags);
        out.sort(Comparator.naturalOrder());
        return out;
    }
}
