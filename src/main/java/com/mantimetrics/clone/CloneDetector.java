package com.mantimetrics.clone;

import com.github.javaparser.ast.body.MethodDeclaration;
import com.mantimetrics.parser.ParsedSourceFile;
import com.mantimetrics.parser.SourceScanResult;
import net.sourceforge.pmd.cpd.CPDConfiguration;
import net.sourceforge.pmd.cpd.CpdAnalysis;
import net.sourceforge.pmd.cpd.Match;
import net.sourceforge.pmd.cpd.Mark;
import net.sourceforge.pmd.lang.document.FileId;
import net.sourceforge.pmd.lang.document.FileLocation;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Computes and caches CPD clone spans for a release so method-level duplication can be queried cheaply.
 */
public class CloneDetector {

    private static final int MINIMUM_TILE_SIZE = 50;
    private static final Map<String, Map<String, List<LineSpan>>> CACHE = new ConcurrentHashMap<>();

    /**
     * Prevents instantiation of the static clone-detection utility.
     */
    private CloneDetector() { /* Prevent instantiation */ }

    /**
     * Computes and caches the clone spans for a release, returning the cache key used for later lookups.
     *
     * @param sourceSet release sources to analyze with CPD
     * @return cache key associated with the prepared clone map
     * @throws IOException when CPD analysis fails
     */
    @SuppressWarnings({"UnusedAssignment", "MismatchedJavadocCode"})
    public static String prepareCloneMap(SourceScanResult sourceSet) throws IOException {
        String cacheKey = sourceSet.id();
        Map<String, List<LineSpan>> cloneMap = CACHE.get(cacheKey);
        if (cloneMap == null) {
            Map<String, List<LineSpan>> computed = computeCloneMap(sourceSet);
            Map<String, List<LineSpan>> previous = CACHE.putIfAbsent(cacheKey, computed);
            cloneMap = previous != null ? previous : computed;
        }
        return cacheKey;
    }

    /**
     * Removes a cached clone map once the release processing is complete.
     *
     * @param cacheKey cache key returned by {@link #prepareCloneMap(SourceScanResult)}
     */
    public static void evict(String cacheKey) {
        CACHE.remove(cacheKey);
    }

    /**
     * Reports whether a method is fully contained in at least one CPD clone span.
     *
     * @param cacheKey cache key returned by {@link #prepareCloneMap(SourceScanResult)}
     * @param relUnixPath normalized relative source path
     * @param method method declaration to inspect
     * @return {@code true} when the method falls inside a cloned span
     * @throws IOException when the clone cache was not prepared for the key
     */
    public static boolean isMethodDuplicated(String cacheKey, String relUnixPath, MethodDeclaration method)
            throws IOException {
        Map<String, List<LineSpan>> cloneMap = CACHE.get(cacheKey);
        if (cloneMap == null) {
            throw new IOException("Missing clone cache for " + cacheKey);
        }
        List<LineSpan> spans = cloneMap.getOrDefault(relUnixPath, List.of());
        return method.getRange()
                .map(r -> spans.stream()
                .anyMatch(span -> span.startLine() >= r.begin.line && span.endLine() <= r.end.line))
                .orElse(false);
    }

    /**
     * Runs CPD on the provided release sources and groups clone spans by relative path.
     *
     * @param sourceSet release sources to analyze
     * @return immutable clone-span index
     * @throws IOException when CPD analysis fails
     */
    private static Map<String, List<LineSpan>> computeCloneMap(SourceScanResult sourceSet)
            throws IOException {
        CPDConfiguration config = new CPDConfiguration();
        config.setMinimumTileSize(MINIMUM_TILE_SIZE);
        config.setSourceEncoding(StandardCharsets.UTF_8);

        List<Match> matches = new ArrayList<>();
        try (CpdAnalysis cpd = CpdAnalysis.create(config)) {
            addSources(cpd, sourceSet.includedFiles());
            cpd.performAnalysis(report ->
                    matches.addAll(report.getMatches())
            );
        }

        Map<String, List<LineSpan>> cloneMap = new HashMap<>();
        for (Match match : matches) {
            if (match.getMarkCount() > 1) {
                for (Mark mark : match.getMarkSet()) {
                    addSpan(cloneMap, mark);
                }
            }
        }

        cloneMap.replaceAll((key, spans) -> List.copyOf(spans));
        return Collections.unmodifiableMap(cloneMap);
    }

    /**
     * Registers in-memory source files with the CPD analysis.
     *
     * @param cpd CPD analysis instance
     * @param sources parsed source files to analyze
     */
    private static void addSources(CpdAnalysis cpd, List<ParsedSourceFile> sources) {
        for (ParsedSourceFile source : sources) {
            cpd.files().addSourceFile(FileId.fromPathLikeString(source.relativePath()), source.source());
        }
    }

    /**
     * Adds a clone span to the grouped index using the normalized relative path as key.
     *
     * @param cloneMap grouped clone spans to update
     * @param mark CPD mark describing one side of a clone occurrence
     */
    private static void addSpan(Map<String, List<LineSpan>> cloneMap, Mark mark) {
        FileLocation location = mark.getLocation();
        String relPath = location.getFileId().getOriginalPath().replace('\\', '/');
        cloneMap.computeIfAbsent(relPath, unused -> new ArrayList<>())
                .add(new LineSpan(location.getStartLine(), location.getEndLine()));
    }

    /**
     * Inclusive line interval occupied by a detected clone occurrence.
     *
     * @param startLine first line of the clone span
     * @param endLine last line of the clone span
     */
    private record LineSpan(int startLine, int endLine) {
    }
}
