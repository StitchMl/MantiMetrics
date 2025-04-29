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
     * Extracts static metrics from a GitHub repository using the API.
     *
     * @param repoUrl base URL of the GitHub repository (e.g., <a href="https://api.github.com/repos/apache/avro">...</a>)
     * @param branch Branch to be analyzed (e.g. "main")
     * @param metricsConfig Dynamic metrics configuration
     * @return List of records (one per file/method) with calculated metrics
     */
    public static List<Map<String, Object>> extractFromGitHub(String repoUrl, String branch, MetricsConfiguration metricsConfig) {
        List<Map<String, Object>> records = new ArrayList<>();
        try {
            // URL to get the complete recursive tree
            String treeUrl = repoUrl + "/git/trees/" + branch + "?recursive=1";
            String response = httpGet(treeUrl);
            JSONObject json = new JSONObject(response);
            JSONArray tree = json.getJSONArray("tree");

            // Extract 'owner' and 'repo' from the URL, assuming the format .../repos/owner/repo
            String[] parts = repoUrl.split("/");
            String owner = parts[parts.length - 2];
            String repo = parts[parts.length - 1];

            for (int i = 0; i < tree.length(); i++) {
                JSONObject fileObj = tree.getJSONObject(i);
                String path = fileObj.getString("path");
                String type = fileObj.getString("type");
                if ("blob".equals(type) && path.endsWith(".java")) {
                    // Download the contents of the file
                    String rawUrl = "https://raw.githubusercontent.com/" + owner + "/" + repo + "/" + branch + "/" + path;
                    String fileContent = httpGet(rawUrl);

                    Map<String, Object> record = new HashMap<>();
                    record.put("projectName", metricsConfig.getProjectName());
                    // Format the method name as 'Path::MethodName'; in a real implementation one would use a parser
                    record.put("methodName", path + "::" + extractMethodName(fileContent));
                    // For this example, we consider the branch as the release id
                    record.put("releaseId", branch);

                    // Calculate LOC if enabled
                    if (metricsConfig.getStaticMetrics().contains("LOC")) {
                        int loc = countLOC(fileContent);
                        record.put("LOC", loc);
                    }
                    // Calculates Cyclomatic Complexity if enabled
                    if (metricsConfig.getStaticMetrics().contains("cyclomaticComplexity")) {
                        int cc = calculateCyclomaticComplexity(fileContent);
                        record.put("cyclomaticComplexity", cc);
                    }
                    // Calculate Nesting Depth if enabled
                    if (metricsConfig.getStaticMetrics().contains("nestingDepth")) {
                        int nd = calculateNestingDepth(fileContent);
                        record.put("nestingDepth", nd);
                    }
                    // Calculate Branch Count if enabled (in this simple case we use it equal to cyclomatic complexity)
                    if (metricsConfig.getStaticMetrics().contains("branchCount")) {
                        record.put("branchCount", calculateCyclomaticComplexity(fileContent));
                    }
                    records.add(record);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return records;
    }

    /**
     * Returns the name of the first method found in the file.
     * For a real solution, an AST parser would be used.
     */
    private static String extractMethodName(String fileContent) {
        // Search for a line containing 'void' or return type and '('
        Scanner scanner = new Scanner(fileContent);
        while (scanner.hasNextLine()) {
            String line = scanner.nextLine().trim();
            if ((line.contains(" void ") || line.matches(".*\\s+[A-Za-z0-9_<>\\[\\]]+\\s+.*\\(.*\\).*"))
                    && line.contains("{")) {
                // Simplified: returns the first word followed by "("
                int parenIndex = line.indexOf('(');
                String sub = line.substring(0, parenIndex).trim();
                String[] tokens = sub.split("\\s+");
                return tokens[tokens.length - 1];
            }
        }
        return "unknownMethod";
    }

    private static int countLOC(String fileContent) {
        int count = 0;
        Scanner scanner = new Scanner(fileContent);
        boolean inBlockComment = false;
        while (scanner.hasNextLine()) {
            String line = scanner.nextLine().trim();
            // Manage multi-line comments
            if (line.startsWith("/*")) {
                inBlockComment = true;
            }
            if (!inBlockComment && !line.isEmpty() && !line.startsWith("//")) {
                count++;
            }
            if (inBlockComment && line.endsWith("*/")) {
                inBlockComment = false;
            }
        }
        scanner.close();
        return count;
    }

    private static int calculateCyclomaticComplexity(String fileContent) {
        int complexity = 1; // The default method has complexity 1
        String[] keywords = {"if(", "for(", "while(", "case ", "catch("};
        for (String keyword : keywords) {
            int index = 0;
            while ((index = fileContent.indexOf(keyword, index)) != -1) {
                complexity++;
                index += keyword.length();
            }
        }
        return complexity;
    }

    private static int calculateNestingDepth(String fileContent) {
        int maxDepth = 0;
        int currentDepth = 0;
        for (char c : fileContent.toCharArray()) {
            if (c == '{') {
                currentDepth++;
                if (currentDepth > maxDepth) {
                    maxDepth = currentDepth;
                }
            } else if (c == '}') {
                currentDepth--;
            }
        }
        return maxDepth;
    }

    private static String httpGet(String urlStr) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        // Set User-Agent to avoid blocking by GitHub
        conn.setRequestProperty("User-Agent", "Mozilla/5.0");
        BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        String inputLine;
        StringBuilder responseContent = new StringBuilder();
        while ((inputLine = in.readLine()) != null) {
            responseContent.append(inputLine).append("\n");
        }
        in.close();
        conn.disconnect();
        return responseContent.toString();
    }
}