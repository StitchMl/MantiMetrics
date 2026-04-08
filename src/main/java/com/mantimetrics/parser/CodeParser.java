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

@SuppressWarnings("unused")
public final class CodeParser {
    private static final Logger LOG = LoggerFactory.getLogger(CodeParser.class);

    private final GitService git;
    private final JavaSourceScanner sourceScanner = new JavaSourceScanner();
    private final MethodDataFactory methodDataFactory = new MethodDataFactory();
    private final TypeDataFactory typeDataFactory = new TypeDataFactory();

    public CodeParser(GitService git) {
        this.git = git;
    }

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

    private ParsedSourceFile withKeys(ParsedSourceFile sourceFile, Map<String, List<String>> fileToKeys) {
        return new ParsedSourceFile(
                sourceFile.relativePath(),
                sourceFile.source(),
                fileToKeys.getOrDefault(sourceFile.relativePath(), List.of()));
    }

    private String prepareCloneCache(SourceScanResult scanResult) {
        try {
            return CloneDetector.prepareCloneMap(scanResult);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to precompute clone cache", exception);
        }
    }
}
