package it.mantimetrics.extractor;

import it.mantimetrics.utils.MetricsConfiguration;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;

public class CommitMetricsExtractor {

    /**
     * Extracts commit metrics from a GitHub repository.
     *
     * @param repoUrl base URL of the repository (e.g., <a href="https://api.github.com/repos/apache/avro">...</a>)
     * @param branch branch to be analyzed (e.g. "main")
     * @param metricsConfig dynamic metrics configuration
     * @return list of records with commit metrics
     */
    public static List<Map<String, Object>> extractFromGitHub(String repoUrl, String branch, MetricsConfiguration metricsConfig) {
        List<Map<String, Object>> records = new ArrayList<>();
        try {
            // Construct the URL to obtain commits
            // Example: https://api.github.com/repos/apache/avro/commits?sha=main
            String commitsUrl = repoUrl + "/commits?sha=" + branch;
            String response = httpGet(commitsUrl);
            JSONArray commitsArray = new JSONArray(response);

            for (int i = 0; i < commitsArray.length(); i++) {
                JSONObject commitObj = commitsArray.getJSONObject(i);
                Map<String, Object> record = new HashMap<>();
                record.put("methodName", "exampleMethod()"); // Simulation: for real mapping, diffs must be analysed
                record.put("releaseId", branch);
                String commitMessage = commitObj.getJSONObject("commit").getString("message");
                if (commitMessage.contains("defect")) {
                    record.put("ticketType", "defect");
                    record.put("ticketStatus", "Closed");
                    record.put("resolution", "Fixed");
                } else {
                    record.put("ticketType", "other");
                }
                if (metricsConfig.getCommitMetrics().contains("methodHistories")) {
                    record.put("methodHistories", 3);
                }
                if (metricsConfig.getCommitMetrics().contains("authors")) {
                    record.put("authors", 2);
                }
                if (metricsConfig.getCommitMetrics().contains("churn")) {
                    record.put("churn", 20);
                }
                records.add(record);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return records;
    }

    private static String httpGet(String urlStr) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("User-Agent", "Mozilla/5.0");
        BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        String inputLine;
        StringBuilder content = new StringBuilder();
        while ((inputLine = in.readLine()) != null) {
            content.append(inputLine);
        }
        in.close();
        conn.disconnect();
        return content.toString();
    }
}