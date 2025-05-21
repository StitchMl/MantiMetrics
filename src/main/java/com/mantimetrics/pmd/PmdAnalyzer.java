package com.mantimetrics.pmd;

import net.sourceforge.pmd.PmdAnalysis;
import net.sourceforge.pmd.PMDConfiguration;
import net.sourceforge.pmd.renderers.Renderer;
import net.sourceforge.pmd.renderers.TextRenderer;
import net.sourceforge.pmd.reporting.Report;

import java.nio.file.Path;
import java.util.Collections;
import java.io.StringWriter;

import static net.sourceforge.pmd.lang.LanguageRegistry.PMD;

/**
 * Runs PMD locally on a Java project, returning the Code Smell report.
 */
public class PmdAnalyzer {
    private final PMDConfiguration config;

    public PmdAnalyzer(Path rulesetXml) {
        config = new PMDConfiguration();
        config.setRuleSets(Collections.singletonList(rulesetXml.toString()));
        // adapted to your Java version
        config.setDefaultLanguageVersion(
                PMD.getLanguageVersionById("java", "22"));
        // add the whole Java source
        config.addInputPath(Path.of("src/main/java"));
    }

    /**
     * It analyzes the code in 'sourceDir' and returns the PMD report.
     */
    public Report analyze(Path sourceDir) {
        Renderer renderer = new TextRenderer();
        renderer.setWriter(new StringWriter());

        try (PmdAnalysis analysis = PmdAnalysis.create(config)) {
            analysis.files().addFile(sourceDir);
            return analysis.performAnalysisAndCollectReport();
        }
    }
}