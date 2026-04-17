package com.mantimetrics.analysis;

import com.mantimetrics.util.AnalysisPathUtils;
import net.sourceforge.pmd.reporting.RuleViolation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Indexes PMD rule violations by normalized relative source path to speed up row-level lookups.
 */
final class ReleaseViolationIndex {
    private final Map<String, List<RuleViolation>> violationsByRelativePath;

    /**
     * Creates an immutable index from a map already grouped by normalized relative path.
     *
     * @param violationsByRelativePath grouped rule violations
     */
    private ReleaseViolationIndex(Map<String, List<RuleViolation>> violationsByRelativePath) {
        this.violationsByRelativePath = violationsByRelativePath;
    }

    /**
     * Builds an immutable violation index for the current release.
     *
     * @param violations PMD violations to index
     * @return indexed violations keyed by normalized relative path
     */
    static ReleaseViolationIndex from(List<RuleViolation> violations) {
        Map<String, List<RuleViolation>> byRelativePath = new HashMap<>();
        for (RuleViolation violation : violations) {
            String relativePath = AnalysisPathUtils.normalizeDatasetPath(violation.getFileId().getOriginalPath());
            byRelativePath.computeIfAbsent(relativePath, ignored -> new ArrayList<>()).add(violation);
        }
        byRelativePath.replaceAll((ignored, value) -> List.copyOf(value));
        return new ReleaseViolationIndex(Map.copyOf(byRelativePath));
    }

    /**
     * Counts how many violations fall inside a source-line interval for a file.
     *
     * @param relativePath normalized relative source path
     * @param startLine first line of the row range, inclusive
     * @param endLine last line of the row range, inclusive
     * @return number of matching violations
     */
    int countViolations(String relativePath, int startLine, int endLine) {
        return (int) violationsByRelativePath.getOrDefault(relativePath, List.of()).stream()
                .filter(violation -> violation.getBeginLine() >= startLine && violation.getBeginLine() <= endLine)
                .count();
    }
}
