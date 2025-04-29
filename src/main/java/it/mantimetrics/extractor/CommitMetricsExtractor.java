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
     * Extracts commit metrics from the GitHub repository.
     *
     * @param repoUrl base URL of the repository (e.g., <a href="https://api.github.com/repos/apache/avro">...</a>)
     * @param branch Branch to be analyzed
     * @param metricsConfig Dynamic metrics configuration
     * @return List of records with metrics from the commits
     */
    public static List<Map<String, Object>> extractFromGitHub(String repoUrl, String branch, MetricsConfiguration metricsConfig) {
        List<Map<String, Object>> records = new ArrayList<>();
        try {
            String commitsUrl = repoUrl + "/commits?sha=" + branch;
            String response = httpGet(commitsUrl);
            JSONArray commitsArray = new JSONArray(response);

            for (int i = 0; i < commitsArray.length(); i++) {
                JSONObject commitObj = commitsArray.getJSONObject(i);
                Map<String, Object> record = new HashMap<>();
                // In a real environment, the mapping between commits and modified methods should be done.
                // Here we use a generic identifier approach.
                record.put("methodName", "exampleMethod()");
                record.put("releaseId", branch);

                // Data extraction from a commit
                JSONObject commitData = commitObj.getJSONObject("commit");
                String commitMessage = commitData.getString("message");
                // Determines whether the commit concerns a bug, based on the message
                if (commitMessage.toLowerCase().contains("defect")) {
                    record.put("ticketType", "defect");
                    record.put("ticketStatus", "Closed");
                    record.put("resolution", "Fixed");
                } else {
                    record.put("ticketType", "other");
                    record.put("ticketStatus", "Open");
                    record.put("resolution", "Unresolved");
                }
                // If the configuration requires methodHistories, authors, churn, etc.
                if (metricsConfig.getCommitMetrics().contains("churn")) {
                    int churn = calculateChurnFromCommit(commitObj);
                    record.put("churn", churn);
                }
                if (metricsConfig.getCommitMetrics().contains("methodHistories")) {
                    // For a real implementation it would be necessary to aggregate the data, here we set a more realistic fake value
                    record.put("methodHistories", 1);
                }
                if (metricsConfig.getCommitMetrics().contains("authors")) {
                    // Extract author from commit
                    String author = commitData.getJSONObject("author").optString("name", "unknown");
                    record.put("authors", author);
                }
                records.add(record);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return records;
    }

    private static int calculateChurnFromCommit(JSONObject commitObj) {
        // If the commit has a 'stats' object, use it to calculate churn (additions and deletions)
        if (commitObj.has("stats")) {
            JSONObject stats = commitObj.getJSONObject("stats");
            int additions = stats.optInt("additions", 0);
            int deletions = stats.optInt("deletions", 0);
            return additions + deletions;
        }
        return 0;
    }

    private static String httpGet(String urlStr) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("User-Agent", "Mozilla/5.0");
        BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        StringBuilder responseContent = new StringBuilder();
        String inputLine;
        while ((inputLine = in.readLine()) != null) {
            responseContent.append(inputLine);
        }
        in.close();
        conn.disconnect();
        return responseContent.toString();
    }
}