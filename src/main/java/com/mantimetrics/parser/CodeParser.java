package com.mantimetrics.parser;

import com.mantimetrics.clone.CloneDetector;
import com.mantimetrics.git.GitService;
import com.mantimetrics.metrics.MetricsCalculator;
import com.mantimetrics.model.ClassData;
import com.mantimetrics.model.MethodData;
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
public final class CodeParser {
    private static final Logger LOG = LoggerFactory.getLogger(CodeParser.class);

    private final GitService git;
    private final JavaSourceScanner sourceScanner = new JavaSourceScanner();
    private final MethodDataFactory methodDataFactory = new MethodDataFactory();
    private final TypeDataFactory typeDataFactory = new TypeDataFactory();

    /**
     * Creates a parser backed by the Git service used to download release sources.
     *
     * @param git Git service used to load release source archives
     */
    public CodeParser(GitService git) {
        this.git = git;
    }

    /**
     * Downloads the production sources for a release tag.
     *
     * @param owner repository owner
     * @param repo repository name
     * @param tag release tag to download
     * @return extracted release sources
     * @throws CodeParserException when the download fails or the thread is interrupted
     */
    public SourceScanResult loadReleaseSources(String owner, String repo, String tag) throws CodeParserException {
        try {
            return git.downloadReleaseSources(owner, repo, tag);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new CodeParserException("Interrupted downloading " + tag, exception);
        } catch (IOException exception) {
            throw new CodeParserException("Download failed for " + repo + '@' + tag, exception);
        }
    }

    /**
     * Scans a local directory and parses method rows while owning the clone-cache lifecycle.
     *
     * @param root root directory to scan
     * @param repo project name
     * @param tag release identifier
     * @param calculator metrics calculator
     * @param fileToKeys Jira issue keys grouped by relative path
     * @return parsed method rows
     */
    public List<MethodData> parseFromDirectory(
            Path root,
            String repo,
            String tag,
            MetricsCalculator calculator,
            Map<String, List<String>> fileToKeys
    ) {
        SourceScanResult scanResult = sourceScanner.scan(root, fileToKeys);
        String cloneCacheKey = prepareCloneCache(scanResult);
        try {
            return parseMethods(scanResult, scanResult, repo, tag, cloneCacheKey, calculator, fileToKeys);
        } finally {
            CloneDetector.evict(cloneCacheKey);
        }
    }

    /**
     * Scans a local directory and parses method rows using a precomputed clone cache.
     *
     * @param root root directory to scan
     * @param repo project name
     * @param tag release identifier
     * @param cloneCacheKey clone-cache key prepared in advance
     * @param calculator metrics calculator
     * @param fileToKeys Jira issue keys grouped by relative path
     * @return parsed method rows
     */
    public List<MethodData> parseFromDirectory(
            Path root,
            String repo,
            String tag,
            String cloneCacheKey,
            MetricsCalculator calculator,
            Map<String, List<String>> fileToKeys
    ) {
        SourceScanResult scanResult = sourceScanner.scan(root, fileToKeys);
        return parseMethods(scanResult, scanResult, repo, tag, cloneCacheKey, calculator, fileToKeys);
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
    public List<ClassData> parseClassesFromDirectory(
            Path root,
            String repo,
            String tag,
            MetricsCalculator calculator,
            Map<String, List<String>> fileToKeys
    ) {
        SourceScanResult scanResult = sourceScanner.scan(root, fileToKeys);
        String cloneCacheKey = prepareCloneCache(scanResult);
        try {
            return parseClasses(scanResult, scanResult, repo, tag, cloneCacheKey, calculator, fileToKeys);
        } finally {
            CloneDetector.evict(cloneCacheKey);
        }
    }

    /**
     * Scans a local directory and parses class rows using a precomputed clone cache.
     *
     * @param root root directory to scan
     * @param repo project name
     * @param tag release identifier
     * @param cloneCacheKey clone-cache key prepared in advance
     * @param calculator metrics calculator
     * @param fileToKeys Jira issue keys grouped by relative path
     * @return parsed class rows
     */
    public List<ClassData> parseClassesFromDirectory(
            Path root,
            String repo,
            String tag,
            String cloneCacheKey,
            MetricsCalculator calculator,
            Map<String, List<String>> fileToKeys
    ) {
        SourceScanResult scanResult = sourceScanner.scan(root, fileToKeys);
        return parseClasses(scanResult, scanResult, repo, tag, cloneCacheKey, calculator, fileToKeys);
    }

    /**
     * Parses method rows from an already prepared source scan.
     *
     * @param sourceSet original source scan used for reporting totals
     * @param analyzedSources sources actually parsed
     * @param repo project name
     * @param tag release identifier
     * @param cloneCacheKey clone-cache key prepared for the source set
     * @param calculator metrics calculator
     * @param fileToKeys Jira issue keys grouped by relative path
     * @return parsed method rows
     */
    public List<MethodData> parseMethods(
            SourceScanResult sourceSet,
            SourceScanResult analyzedSources,
            String repo,
            String tag,
            String cloneCacheKey,
            MetricsCalculator calculator,
            Map<String, List<String>> fileToKeys
    ) {
        List<MethodData> methods = new ArrayList<>();

        for (ParsedSourceFile sourceFile : analyzedSources.includedFiles()) {
            methods.addAll(methodDataFactory.collect(cloneCacheKey, withKeys(sourceFile, fileToKeys), repo, tag, calculator));
        }

        LOG.info("[DIRECTORY] release={} filesTotali={} filesProcessati={}",
                tag, sourceSet.totalJavaFiles(), analyzedSources.includedFiles().size());
        return methods;
    }

    /**
     * Parses class rows from an already prepared source scan.
     *
     * @param sourceSet original source scan used for reporting totals
     * @param analyzedSources sources actually parsed
     * @param repo project name
     * @param tag release identifier
     * @param cloneCacheKey clone-cache key prepared for the source set
     * @param calculator metrics calculator
     * @param fileToKeys Jira issue keys grouped by relative path
     * @return parsed class rows
     */
    public List<ClassData> parseClasses(
            SourceScanResult sourceSet,
            SourceScanResult analyzedSources,
            String repo,
            String tag,
            String cloneCacheKey,
            MetricsCalculator calculator,
            Map<String, List<String>> fileToKeys
    ) {
        List<ClassData> classes = new ArrayList<>();

        for (ParsedSourceFile sourceFile : analyzedSources.includedFiles()) {
            classes.addAll(typeDataFactory.collect(cloneCacheKey, withKeys(sourceFile, fileToKeys), repo, tag, calculator));
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
    private ParsedSourceFile withKeys(ParsedSourceFile sourceFile, Map<String, List<String>> fileToKeys) {
        return new ParsedSourceFile(
                sourceFile.relativePath(),
                sourceFile.source(),
                fileToKeys.getOrDefault(sourceFile.relativePath(), List.of()));
    }

    /**
     * Prepares the clone cache for a source scan and wraps failures into an unchecked exception.
     *
     * @param scanResult source scan to analyze for clones
     * @return clone-cache key
     */
    private String prepareCloneCache(SourceScanResult scanResult) {
        try {
            return CloneDetector.prepareCloneMap(scanResult);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to precompute clone cache", exception);
        }
    }
}
