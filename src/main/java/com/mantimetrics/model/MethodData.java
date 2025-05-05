package com.mantimetrics.model;

import com.mantimetrics.metrics.MethodMetrics;
import java.util.List;

public class MethodData {
    private final String projectName;
    private final String path;
    private final String methodSignature;
    private final String releaseId;
    private final String versionId;
    private final String commitId;
    private final MethodMetrics metrics;
    private final List<String> commitHashes;
    private boolean buggy;

    public MethodData(String projectName,
                      String methodPath,
                      String methodSignature,
                      String releaseId,
                      String versionId,
                      String commitId,
                      MethodMetrics metrics,
                      List<String> commitHashes) {
        this.projectName     = projectName.toUpperCase();
        this.path            = methodPath;
        this.methodSignature = methodSignature;
        this.releaseId       = releaseId;
        this.versionId       = versionId;
        this.commitId        = commitId;
        this.metrics         = metrics;
        this.commitHashes    = commitHashes;
    }

    public List<String> getCommitHashes() { return commitHashes; }
    public void setBuggy(boolean buggy) { this.buggy = buggy; }

    public String toCsvLine() {
        // Construct the feature list in a consistent order
        String feats = String.join(",",
                // Complexity & Size
                String.valueOf(metrics.getLoc()),
                String.valueOf(metrics.getStmtCount()),
                String.valueOf(metrics.getCyclomatic()),
                String.valueOf(metrics.getCognitive()),
                // Halstead
                String.valueOf(metrics.getDistinctOperators()),   // n1
                String.valueOf(metrics.getDistinctOperands()),    // n2
                String.valueOf(metrics.getTotalOperators()),      // N1
                String.valueOf(metrics.getTotalOperands()),       // N2
                String.valueOf(metrics.getVocabulary()),          // n
                String.valueOf(metrics.getLength()),              // N
                String.valueOf(metrics.getVolume()),              // V
                String.valueOf(metrics.getDifficulty()),          // D
                String.valueOf(metrics.getEffort()),              // E
                // Nesting
                String.valueOf(metrics.getMaxNestingDepth()),
                // Code smells (1 = present, 0 = absent)
                metrics.isLongMethod()     ? "1" : "0",
                metrics.isGodClass()       ? "1" : "0",
                metrics.isFeatureEnvy()    ? "1" : "0",
                metrics.isDuplicatedCode() ? "1" : "0"
        );

        // Build the complete CSV line
        return String.join(",",
                projectName,
                path,
                "\"" + methodSignature.replace("\"", "\"\"") + "\"", // escape double inverted commas
                releaseId,
                versionId,
                commitId,
                feats,
                buggy ? "yes" : "no"
        );
    }
}