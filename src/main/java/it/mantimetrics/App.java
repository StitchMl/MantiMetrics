package it.mantimetrics;

import it.mantimetrics.extractor.CommitMetricsExtractor;
import it.mantimetrics.extractor.StaticMetricsExtractor;
import it.mantimetrics.merger.DataMerger;
import it.mantimetrics.utils.ConfigLoader;
import it.mantimetrics.utils.MetricsConfiguration;
import it.mantimetrics.utils.MetricsLogger;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import java.io.FileWriter;
import java.util.List;
import java.util.Map;

public class App {
    public static void main(String[] args) {
        try {
            if (args.length < 1) {
                System.err.println("Usage: mvn exec:java \"-Dexec.mainClass=it.mantimetrics.App\" \"-Dexec.args=config/config_avro.properties\"");
                System.exit(1);
            }

            // Upload configuration file
            String configFile = args[0];
            Map<String, String> config = ConfigLoader.load(configFile);

            // Load dynamic metrics configuration
            MetricsConfiguration metricsConfig = new MetricsConfiguration(config);

            // Log of selected metrics
            MetricsLogger.logChosenMetrics(config);

            // Reads parameters for the remote repository
            String repoUrl = config.get("repo.url");
            String branch = config.get("branch");
            if (repoUrl == null || branch == null) {
                System.err.println("The properties 'repo.url' or 'branch' are not defined in the configuration file.");
                System.exit(1);
            }

            // Extracts static metrics from the GitHub repository
            List<Map<String, Object>> staticMetrics = StaticMetricsExtractor.extractFromGitHub(repoUrl, branch, metricsConfig);

            // Extracts commit metrics from the GitHub repository
            List<Map<String, Object>> commitMetrics = CommitMetricsExtractor.extractFromGitHub(repoUrl, branch, metricsConfig);

            // Merges datasets (static and commit)
            List<Map<String, Object>> mergedData = DataMerger.merge(staticMetrics, commitMetrics, config);

            // Writes the final CSV
            String csvOutput = config.get("output.csv");
            writeCSV(mergedData, csvOutput);
            System.out.println("CSV successfully generated in: " + csvOutput);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void writeCSV(List<Map<String, Object>> data, String outputPath) throws Exception {
        if (data.isEmpty()) {
            System.out.println("No data to be written in the CSV.");
            return;
        }
        try (FileWriter out = new FileWriter(outputPath);
             CSVPrinter printer = new CSVPrinter(out, CSVFormat.DEFAULT.withHeader(data.get(0).keySet().toArray(new String[0])))) {
            for (Map<String, Object> record : data) {
                printer.printRecord(record.values());
            }
        }
    }
}