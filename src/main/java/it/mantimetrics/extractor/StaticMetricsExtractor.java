package it.mantimetrics.extractor;

import it.mantimetrics.utils.MetricsConfiguration;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;

public class StaticMetricsExtractor {

    /**
     * Extracts static metrics from a GitHub repository.
     * Uses the API to get the "tree" of the repository and download the .java files.
     *
     * @param repoUrl base URL of the GitHub repository (e.g., <a href="https://api.github.com/repos/apache/avro">...</a>)
     * @param branch to be analyzed (e.g. "main")
     * @param metricsConfig dynamic metrics configuration
     * @return list of records with extracted metrics
     */
    public static List<Map<String, Object>> extractFromGitHub(String repoUrl, String branch, MetricsConfiguration metricsConfig) {
        List<Map<String, Object>> records = new ArrayList<>();
        try {
            // Construct the URL to obtain the complete tree (recursive)
            // Example: https://api.github.com/repos/apache/avro/git/trees/main?recursive=1
            String treeUrl = repoUrl + "/git/trees/" + branch + "?recursive=1";
            String response = httpGet(treeUrl);
            JSONObject json = new JSONObject(response);
            JSONArray tree = json.getJSONArray("tree");
            // Extract owner and repo from repoUrl (assumes a format: https://api.github.com/repos/owner/repo)
            String[] parts = repoUrl.split("/");
            String owner = parts[parts.length - 2];
            String repo = parts[parts.length - 1];

            for (int i = 0; i < tree.length(); i++) {
                JSONObject fileObj = tree.getJSONObject(i);
                String path = fileObj.getString("path");
                String type = fileObj.getString("type");
                if ("blob".equals(type) && path.endsWith(".java")) {
                    // Construct the raw URL to download the file
                    // Example: https://raw.githubusercontent.com/apache/avro/main/{path}
                    String rawUrl = "https://raw.githubusercontent.com/" + owner + "/" + repo + "/" + branch + "/" + path;
                    String fileContent = httpGet(rawUrl);

                    // Simulates the extraction of metrics from the contents of the file
                    Map<String, Object> record = new HashMap<>();
                    record.put("projectName", metricsConfig.getProjectName());
                    // We use the path and an example method to compose the method name
                    record.put("methodName", path + "::exampleMethod()");
                    record.put("releaseId", branch); // For demos, we use the branch as an identifier
                    if (metricsConfig.getStaticMetrics().contains("LOC")) {
                        // Calculates the lines in the file
                        record.put("LOC", fileContent.split("\n").length);
                    }
                    if (metricsConfig.getStaticMetrics().contains("cyclomaticComplexity")) {
                        record.put("cyclomaticComplexity", 10); // dummy value
                    }
                    if (metricsConfig.getStaticMetrics().contains("nestingDepth")) {
                        record.put("nestingDepth", 3);
                    }
                    if (metricsConfig.getStaticMetrics().contains("branchCount")) {
                        record.put("branchCount", 7);
                    }
                    records.add(record);
                }
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