package it.mantimetrics.utils;

import java.io.File;
import java.util.Map;

public class FileUtils {

    /**
     * Check that the paths indicated in the configuration file exist and are valid.
     * If the output path (output.csv) does not exist, it attempts to create the directory.
     *
     * @param config The configuration map loaded from the properties file.
     * @throws IllegalArgumentException if a mandatory route is not valid.
     * @throws IllegalStateException if it fails to create the output directory.
     */
    public static void validatePaths(Map<String, String> config) {
        // Source code path validation
        String codeDirectory = config.get("code.directory");
        if (codeDirectory == null || codeDirectory.trim().isEmpty()) {
            throw new IllegalArgumentException("The property 'code.directory' is not specified in the configuration file.");
        }
        File codeDir = new File(codeDirectory);
        if (!codeDir.exists() || !codeDir.isDirectory()) {
            throw new IllegalArgumentException("Invalid code directory: " + codeDirectory);
        }

        // Validation of the repository path for commits
        String commitRepoDirectory = config.get("commit.repo.directory");
        if (commitRepoDirectory == null || commitRepoDirectory.trim().isEmpty()) {
            throw new IllegalArgumentException("La property 'commit.repo.directory' non è specificata nel file di configurazione.");
        }
        File commitDir = new File(commitRepoDirectory);
        if (!commitDir.exists() || !commitDir.isDirectory()) {
            throw new IllegalArgumentException("Invalid commit repository directory: " + commitRepoDirectory);
        }

        // Validation or creation of the output directory for the CSV
        String outputCSV = config.get("output.csv");
        if (outputCSV == null || outputCSV.trim().isEmpty()) {
            throw new IllegalArgumentException("The property 'output.csv' is not specified in the configuration file.");
        }
        File outputFile = new File(outputCSV);
        File outputDir = outputFile.getParentFile();
        if (outputDir != null && !outputDir.exists()) {
            boolean created = outputDir.mkdirs();
            if (!created) {
                throw new IllegalStateException("Impossibile creare una directory di output: " + outputDir.getAbsolutePath());
            }
        }
    }
}
