package it.mantimetrics.extractor;

import it.mantimetrics.utils.MetricsConfiguration;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.LogCommand;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.CredentialsProvider;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CommitMetricsExtractor {

    /**
     * Extracts metrics from commits to a remote Git repository.
     *
     * @param repoUrl URL of the remote Git repository
     * @param metricsConfig The dynamic metrics configuration
     * @param credentialsProvider The credentials for remote access (if needed)
     * @return list of records with metrics extracted from commits
     */
    public static List<Map<String, Object>> extractFromRemoteRepo(String repoUrl, MetricsConfiguration metricsConfig, String branch, CredentialsProvider credentialsProvider) {
        List<Map<String, Object>> records = new ArrayList<>();
        Git git = null;
        try {
            // Clone the repository temporarily
            File tempDir = File.createTempFile("tempRepo", "");
            tempDir.delete();  // Ensure it's treated as a directory
            git = Git.cloneRepository()
                    .setURI(repoUrl)
                    .setDirectory(tempDir)
                    .setCredentialsProvider(credentialsProvider) // Provide credentials if necessary
                    .call();

            // Retrieve the commits from the repository
            Repository repository = git.getRepository();
            LogCommand log = git.log();
            Iterable<RevCommit> commits = log.call();

            // Process each commit and collect metrics
            for (RevCommit commit : commits) {
                List<Map<String, Object>> commitRecords = processCommit(commit, metricsConfig);
                records.addAll(commitRecords);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            // Clean up the cloned repository after processing
            if (git != null) {
                File tempDir = git.getRepository().getDirectory().getParentFile();
                deleteDirectory(tempDir);  // Remove the temp directory after analysis
            }
        }
        return records;
    }

    /**
     * Processes a single commit to simulate the extraction of configured metrics.
     * In a real implementation, the method should parse the changes (diff) and map the changes to the methods.
     */
    private static List<Map<String, Object>> processCommit(RevCommit commit, MetricsConfiguration metricsConfig) {
        List<Map<String, Object>> records = new ArrayList<>();
        Map<String, Object> record = new HashMap<>();

        // Basic information: fake values for the associated method
        record.put("methodName", "exampleMethod()");
        record.put("releaseId", "v1.0");

        // Dynamic calculation of commit metrics
        if (metricsConfig.getCommitMetrics().contains("methodHistories")) {
            record.put("methodHistories", 3);  // Simulate
        }
        if (metricsConfig.getCommitMetrics().contains("authors")) {
            record.put("authors", 2);  // Simulate
        }
        if (metricsConfig.getCommitMetrics().contains("stmtAdded")) {
            record.put("stmtAdded", 15);  // Simulate
        }
        if (metricsConfig.getCommitMetrics().contains("stmtDeleted")) {
            record.put("stmtDeleted", 5);  // Simulate
        }
        if (metricsConfig.getCommitMetrics().contains("churn")) {
            record.put("churn", 20);  // Simulate
        }
        if (metricsConfig.getCommitMetrics().contains("maxStmtAdded")) {
            record.put("maxStmtAdded", 10);  // Simulate
        }
        if (metricsConfig.getCommitMetrics().contains("avgStmtAdded")) {
            record.put("avgStmtAdded", 7);  // Simulate
        }
        if (metricsConfig.getCommitMetrics().contains("maxStmtDeleted")) {
            record.put("maxStmtDeleted", 4);  // Simulate
        }
        if (metricsConfig.getCommitMetrics().contains("avgStmtDeleted")) {
            record.put("avgStmtDeleted", 2);  // Simulate
        }

        // Check if the commit message contains a reference to a 'defect'
        String commitMsg = commit.getShortMessage();
        if (commitMsg != null && commitMsg.contains("defect")) {
            record.put("ticketType", "defect");
            record.put("ticketStatus", "Closed");
            record.put("resolution", "Fixed");
        } else {
            record.put("ticketType", "other");
        }

        records.add(record);
        return records;
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