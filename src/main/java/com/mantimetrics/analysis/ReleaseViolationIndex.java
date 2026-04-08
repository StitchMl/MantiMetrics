package com.mantimetrics.analysis;

import com.mantimetrics.util.AnalysisPathUtils;
import net.sourceforge.pmd.reporting.RuleViolation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class ReleaseViolationIndex {
    private final Map<String, List<RuleViolation>> violationsByRelativePath;

    private ReleaseViolationIndex(Map<String, List<RuleViolation>> violationsByRelativePath) {
        this.violationsByRelativePath = violationsByRelativePath;
    }

    static ReleaseViolationIndex from(List<RuleViolation> violations) {
        Map<String, List<RuleViolation>> byRelativePath = new HashMap<>();
        for (RuleViolation violation : violations) {
            String relativePath = AnalysisPathUtils.normalizeDatasetPath(violation.getFileId().getOriginalPath());
            byRelativePath.computeIfAbsent(relativePath, ignored -> new ArrayList<>()).add(violation);
        }
        byRelativePath.replaceAll((ignored, value) -> List.copyOf(value));
        return new ReleaseViolationIndex(Map.copyOf(byRelativePath));
    }

    int countViolations(String relativePath, int startLine, int endLine) {
        return (int) violationsByRelativePath.getOrDefault(relativePath, List.of()).stream()
                .filter(violation -> violation.getBeginLine() >= startLine && violation.getBeginLine() <= endLine)
                .count();
    }
}
