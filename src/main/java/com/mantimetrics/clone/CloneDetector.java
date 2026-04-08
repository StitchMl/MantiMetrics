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

public class CloneDetector {

    private static final int MINIMUM_TILE_SIZE = 50;
    private static final Map<String, Map<String, List<LineSpan>>> CACHE = new ConcurrentHashMap<>();

    private CloneDetector() { /* Prevent instantiation */ }

    /**
     * Returns true if the method is duplicated (part of at least one clone).
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

    public static void evict(String cacheKey) {
        CACHE.remove(cacheKey);
    }

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

    private static void addSources(CpdAnalysis cpd, List<ParsedSourceFile> sources) {
        for (ParsedSourceFile source : sources) {
            cpd.files().addSourceFile(FileId.fromPathLikeString(source.relativePath()), source.source());
        }
    }

    private static void addSpan(Map<String, List<LineSpan>> cloneMap, Mark mark) {
        FileLocation location = mark.getLocation();
        String relPath = location.getFileId().getOriginalPath().replace('\\', '/');
        cloneMap.computeIfAbsent(relPath, unused -> new ArrayList<>())
                .add(new LineSpan(location.getStartLine(), location.getEndLine()));
    }

    private record LineSpan(int startLine, int endLine) {
    }
}
