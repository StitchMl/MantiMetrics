package com.mantimetrics.javaparsing;

import com.mantimetrics.git.GitFacade;
import com.mantimetrics.feature.MetricsCalculator;
import com.mantimetrics.datasetsetting.DatasetClassData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * High-level parser facade used to load release sources and build class-level or method-level dataset rows.
 */
@SuppressWarnings("unused")
public final class JavaSourceParser {
    private static final Logger LOG = LoggerFactory.getLogger(JavaSourceParser.class);

    private final GitFacade git;
    private final JavaTreeScanner sourceScanner = new JavaTreeScanner();
    private final JavaClassDataASTBuilder typeDataFactory = new JavaClassDataASTBuilder();

    /**
     * Creates a parser backed by the Git service used to download release sources.
     *
     * @param git Git service used to load release source archives
     */
    public JavaSourceParser(GitFacade git) {
        this.git = git;
    }

    /**
     * Downloads the production sources for a release tag.
     *
     * @param owner repository owner
     * @param repo repository name
     * @param tag release tag to download
     * @return extracted release sources
     * @throws JavaParsingException when the download fails or the thread is interrupted
     */
    public ScanResult loadReleaseSources(String owner, String repo, String tag) throws JavaParsingException {
        try {
            return git.downloadReleaseSources(owner, repo, tag);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new JavaParsingException("Interrupted downloading " + tag, exception);
        } catch (IOException exception) {
            throw new JavaParsingException("Download failed for " + repo + '@' + tag, exception);
        }
    }

    /**
     * Scans a local directory and parses class rows while owning the clone-cache lifecycle.
     *
     * @param root root directory to scan
     * @param repo project name
     * @param tag release identifier
     * @param calculator metrics calculator
     * @param fileToKeys Jira issue keys grouped by relative path
     * @return parsed class rows
     */
    public List<DatasetClassData> parseClassesFromDirectory(
            Path root,
            String repo,
            String tag,
            MetricsCalculator calculator,
            Map<String, List<String>> fileToKeys
    ) {
        ScanResult scanResult = sourceScanner.scan(root, fileToKeys);
        return parseClasses(scanResult, scanResult, repo, tag, calculator, fileToKeys);
    }

    /**
     * Parses class rows from an already prepared source scan.
     *
     * @param sourceSet original source scan used for reporting totals
     * @param analyzedSources sources actually parsed
     * @param repo project name
     * @param tag release identifier
     * @param calculator metrics calculator
     * @param fileToKeys Jira issue keys grouped by relative path
     * @return parsed class rows
     */
    public List<DatasetClassData> parseClasses(
            ScanResult sourceSet,
            ScanResult analyzedSources,
            String repo,
            String tag,
            MetricsCalculator calculator,
            Map<String, List<String>> fileToKeys
    ) {
        List<DatasetClassData> classes = new ArrayList<>();

        for (ParsedFileRappresentation sourceFile : analyzedSources.includedFiles()) {
            classes.addAll(typeDataFactory.collect(withKeys(sourceFile, fileToKeys), repo, tag, calculator));
        }

        LOG.info("[CLASS] release={} filesTotali={} filesProcessati={}",
                tag, sourceSet.totalJavaFiles(), analyzedSources.includedFiles().size());
        return classes;
    }

    /**
     * Returns a copy of the parsed source file enriched with its Jira issue keys.
     *
     * @param sourceFile parsed source file
     * @param fileToKeys Jira issue keys grouped by relative path
     * @return source file carrying the associated Jira keys
     */
    private ParsedFileRappresentation withKeys(ParsedFileRappresentation sourceFile, Map<String, List<String>> fileToKeys) {
        return new ParsedFileRappresentation(
                sourceFile.relativePath(),
                sourceFile.source(),
                fileToKeys.getOrDefault(sourceFile.relativePath(), List.of()));
    }

}
