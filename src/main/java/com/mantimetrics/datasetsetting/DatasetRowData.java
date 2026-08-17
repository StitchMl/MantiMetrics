package com.mantimetrics.datasetsetting;

import com.mantimetrics.feature.ClassMetrics;

import java.util.List;

/**
 * Immutable class-level dataset payload (Milestone 1 lean schema).
 *
 * @param projectName analyzed project name
 * @param path normalized relative source path
 * @param releaseId release identifier
 * @param metrics class metrics (LOC, WMC, LCOM)
 * @param commitHashes commits touching the class in the current release range
 * @param buggy whether the class is historically labeled buggy in the current release
 * @param codeSmells SonarCloud code-smell count (NSmells)
 * @param touches commits touching the class in the current release (NR)
 * @param totalTouches cumulative touches across releases
 * @param issueTouches issue-linked touches in the current release (NFix)
 * @param totalIssueTouches cumulative issue-linked touches
 * @param authors distinct authors in the current release (NAuth)
 * @param totalAuthors cumulative distinct authors
 * @param addedLines lines added in the current release
 * @param deletedLines lines deleted in the current release
 * @param churn sum of added and deleted lines in the current release
 * @param totalChurn cumulative churn across releases
 * @param prevCodeSmells NSmells observed in the previous release
 * @param prevBuggy whether the class was buggy in the previous release
 * @param ageInReleases number of analyzed releases in which the class exists
 * @param startLine inclusive start line
 * @param endLine inclusive end line
 * @param maxLoc maximum LOC across releases
 * @param maxWmc maximum WMC across releases
 * @param maxNSmells maximum NSmells across releases
 * @param priorityMax max ticket priority rank (TLP)
 * @param priorityAvg mean ticket priority rank (TLP)
 * @param typeRiskMax max ticket type-risk (TLP)
 * @param typeRiskAvg mean ticket type-risk (TLP)
 * @param componentCountMax max ticket component count (TLP)
 * @param componentCountAvg mean ticket component count (TLP)
 * @param openTickets tickets open at the release snapshot (TLP)
 * @param tlccLin Temporal Locality (linear weighting)
 * @param tlccLog Temporal Locality (logarithmic weighting)
 */
record DatasetRowData(String projectName, String path, String releaseId, ClassMetrics metrics,
                      List<String> commitHashes, boolean buggy, int codeSmells, int touches, int totalTouches,
                      int issueTouches, int totalIssueTouches, int authors, int totalAuthors, int addedLines,
                      int deletedLines, int churn, int totalChurn, int prevCodeSmells, boolean prevBuggy,
                      int ageInReleases, int startLine, int endLine, int maxLoc, int maxWmc, int maxNSmells,
                      int priorityMax, double priorityAvg, int typeRiskMax, double typeRiskAvg,
                      int componentCountMax, double componentCountAvg, int openTickets,
                      double tlccLin, double tlccLog) {

    /** Copies the commit hash list defensively. */
    DatasetRowData {
        commitHashes = List.copyOf(commitHashes);
    }

    /**
     * Returns the NSmells count. Since local smell detectors were removed, NSmells equals
     * the SonarCloud code-smell count.
     *
     * @return NSmells count
     */
    int nSmells() {
        return codeSmells;
    }
}
