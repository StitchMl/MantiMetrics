package it.mantimetrics.extractor;

import it.mantimetrics.utils.MetricsConfiguration;
import org.eclipse.jgit.api.Git;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class StaticMetricsExtractor {

    /**
     * Extracts static metrics from Java code in a remote Git repository.
     *
     * @param repoUrl the URL of the Git repository (remote)
     * @param branch  the branch of the repository to analyze
     * @param metricsConfig the dynamic metrics configuration
     * @return list of records with static metrics
     */
    public static List<Map<String, Object>> extract(String repoUrl, String branch, MetricsConfiguration metricsConfig) {
        List<Map<String, Object>> records = new ArrayList<>();
        File tempDir = null;
        try {
            // Clone the repository to a temporary directory
            tempDir = File.createTempFile("repo", "");
            tempDir.delete();
            Git.cloneRepository()
                    .setURI(repoUrl)
                    .setBranch(branch)
                    .setDirectory(tempDir)
                    .call();

            // Here you would call your existing static analysis logic on the cloned files
            // For now, assuming this is a placeholder
            records.addAll(performStaticAnalysis(tempDir, metricsConfig));
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            // Clean up temporary directory if it exists
            if (tempDir != null && tempDir.exists()) {
                deleteDirectory(tempDir);
            }
        }
        return records;
    }

    /**
     * Placeholder for static analysis logic on the cloned repository.
     * In a real implementation, you would analyze the Java files inside the tempDir.
     */
    private static List<Map<String, Object>> performStaticAnalysis(File repoDir, MetricsConfiguration metricsConfig) {
        // Static metrics extraction logic here
        // This could be parsing the .java files inside repoDir and calculating metrics like LOC, cyclomaticComplexity, etc.
        List<Map<String, Object>> staticMetrics = new ArrayList<>();

        // Add some fake metrics for illustration
        // In a real implementation, replace this with actual calculations
        Map<String, Object> record = Map.of(
                "methodName", "exampleMethod()",
                "LOC", 100,
                "cyclomaticComplexity", 5
        );
        staticMetrics.add(record);
        return staticMetrics;
    }

    /**
     * Deletes a directory and its contents recursively.
     */
    private static void deleteDirectory(File directory) {
        if (directory.isDirectory()) {
            for (File file : directory.listFiles()) {
                deleteDirectory(file);
            }
        }
        directory.delete();
    }
}