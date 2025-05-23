package com.mantimetrics.pmd;

import net.sourceforge.pmd.PmdAnalysis;
import net.sourceforge.pmd.PMDConfiguration;
import net.sourceforge.pmd.lang.document.FileCollector;
import net.sourceforge.pmd.lang.document.TextFile;
import net.sourceforge.pmd.lang.rule.RulePriority;
import net.sourceforge.pmd.reporting.Report;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static net.sourceforge.pmd.lang.LanguageRegistry.PMD;

/**
 * Runs PMD locally on a Java project, returning the Code Smell report.
 */
public class PmdAnalyzer {
    private final PMDConfiguration config;
    private final Logger logger = LoggerFactory.getLogger(PmdAnalyzer.class);

    public PmdAnalyzer() {
        config = new PMDConfiguration();

        // 1) Load ruleset from filesystem or classpath
        config.addRuleSet("category/java/bestpractices.xml");

        // 2) Set Java 22 for parsing
        config.setDefaultLanguageVersion(
                PMD.getLanguageVersionById("java", "22"));

        // The classpath of the compiled classes (if needed for Type Resolution)
        config.prependAuxClasspath("target/classes");
    }

    /**
     * It analyzes the code in 'sourceDir' and returns the PMD report.
     */
    public Report analyze(Path releaseDir) {
        // 1) Duplicates the basic configuration
        PMDConfiguration cfg = new PMDConfiguration();

        // 2) I copy all rulesets already loaded in 'config'.
        cfg.setRuleSets(new ArrayList<>(config.getRuleSetPaths()));
        cfg.setMinimumPriority(RulePriority.LOW);

        // 3) I set Java 22 (copy from the original config, or reallocate it)
        cfg.setDefaultLanguageVersion(PMD.getLanguageVersionById("java", "22"));

        // 4) I also copy the auxClasspath if needed
        cfg.prependAuxClasspath("target/classes");

        // 5) I only configure the input path on the release
        cfg.setInputPathList(List.of(releaseDir));

        // 6) Perform the analysis
        try (PmdAnalysis analysis = PmdAnalysis.create(cfg)) {
            // 6.1) Check which .java files are actually collected
            FileCollector collector = analysis.files();
            List<TextFile> paths = collector.getCollectedFiles();
            logger.debug("File .java trovati: {}", paths.size());

            // 6.2) Perform analysis on all .java recursively
            Report report = analysis.performAnalysisAndCollectReport();

            // 6.3) Checking ruleset loading
            int loaded = analysis.getRulesets().size();
            logger.debug("Rulesets caricate: {}", loaded);

            // 6.4) Log any parsing errors
            report.getProcessingErrors()
                    .forEach(e -> logger.error("PMD error: {}", e.getMsg()));

            return report;
        }
    }
}