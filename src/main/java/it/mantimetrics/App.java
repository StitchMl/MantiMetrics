package it.mantimetrics;

import it.mantimetrics.extractor.CommitMetricsExtractor;
import it.mantimetrics.extractor.StaticMetricsExtractor;
import it.mantimetrics.merger.DataMerger;
import it.mantimetrics.utils.ConfigLoader;
import it.mantimetrics.utils.MetricsConfiguration;
import it.mantimetrics.utils.MetricsLogger;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;

import java.io.FileWriter;
import java.util.List;
import java.util.Map;

public class App {
    public static void main(String[] args) {
        try {
            if (args.length < 1) {
                System.err.println("Usage: mvn clean compile exec:java \"-Dexec.mainClass=it.mantimetrics.App\" \"-Dexec.args=config/config_avro.properties\"");
                System.exit(1);
            }

            // Upload configuration file
            String configFile = args[0];
            Map<String, String> config = ConfigLoader.load(configFile);

            // Load dynamic metrics configuration
            MetricsConfiguration metricsConfig = new MetricsConfiguration(config);
            CredentialsProvider credentialsProvider = new UsernamePasswordCredentialsProvider("username", "password"); // Se necessario

            // Log of selected metrics (lists will be read and printed)
            MetricsLogger.logChosenMetrics(config);

            // Extracting static metrics from remote Git repository
            String repoUrl = config.get("git.repoUrl");
            String branch = config.get("git.branch");
            List<Map<String, Object>> staticMetrics = StaticMetricsExtractor.extract(repoUrl, branch, metricsConfig);

            // Extracting metrics from commits from the Git repository (remote)
            List<Map<String, Object>> commitMetrics = CommitMetricsExtractor.extractFromRemoteRepo(config.get("git.repoUrl"), metricsConfig, branch, credentialsProvider);

            // Fusion of datasets (static and commit)
            List<Map<String, Object>> mergedData = DataMerger.merge(staticMetrics, commitMetrics, config);

            // Writing the final CSV
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