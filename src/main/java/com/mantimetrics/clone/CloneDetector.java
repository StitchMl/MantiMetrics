package com.mantimetrics.clone;

import com.github.javaparser.ast.body.MethodDeclaration;
import net.sourceforge.pmd.cpd.CPDConfiguration;
import net.sourceforge.pmd.cpd.CpdAnalysis;
import net.sourceforge.pmd.cpd.Match;
import net.sourceforge.pmd.cpd.Mark;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class CloneDetector {

    private static final int MINIMUM_TILE_SIZE = 50;

    private CloneDetector() { /* Prevent instantiation */ }

    /**
     * Returns true if the method is duplicated (part of at least one clone).
     */
    public static boolean isMethodDuplicated(MethodDeclaration method) throws SourceCollectionException, IOException {
        // 1) I configure CPD
        CPDConfiguration config = new CPDConfiguration();
        config.setMinimumTileSize(MINIMUM_TILE_SIZE);
        config.setSourceEncoding(StandardCharsets.UTF_8);

        // 2) I set up source paths
        List<Path> sources = SourceCollector.collectJavaSources();
        config.setInputPathList(sources);

        // 3) CPD analysis with consumer
        List<Match> matches = new ArrayList<>();
        try (CpdAnalysis cpd = CpdAnalysis.create(config)) {
            cpd.performAnalysis(report ->
                    matches.addAll(report.getMatches())
            );
        }

        // 4) Clone verification
        for (Match match : matches) {
            if (match.getMarkCount() > 1) {
                for (Mark mark : match.getMarkSet()) {
                    if (isWithin(method, mark)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** Check whether the mark falls entirely within the AST range of the method. */
    private static boolean isWithin(MethodDeclaration method, Mark mark) {
        return method.getRange()
                .map(r -> mark.getBeginTokenIndex() >= r.begin.line
                        && mark.getEndTokenIndex()   <= r.end.line)
                .orElse(false);
    }
}