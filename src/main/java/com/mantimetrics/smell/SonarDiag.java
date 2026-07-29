package com.mantimetrics.smell;

import com.mantimetrics.config.SonarTokenLoader;

import java.util.List;
import java.util.Map;

/**
 * Standalone diagnostic for SonarCloud connectivity and smell-path format. It hits ONLY the
 * SonarCloud API (no GitHub / no source parsing), so it runs in seconds:
 *
 * <pre>
 *   mvnw -q exec:java "-Dexec.mainClass=com.mantimetrics.smell.SonarDiag"
 *   mvnw -q exec:java "-Dexec.mainClass=com.mantimetrics.smell.SonarDiag" "-Dexec.args=StitchMl_avro"
 * </pre>
 *
 * It prints: whether a token was loaded, how many analyses exist, and — for the earliest analysis —
 * how many files carry code smells plus a few sample paths, so they can be compared with the dataset
 * paths (e.g. {@code lang/java/src/java/org/apache/avro/...}).
 */
@SuppressWarnings({"java:S106", "UseOfSystemOutOrSystemErr"})
public final class SonarDiag {

    private SonarDiag() {
    }

    /**
     * Runs the diagnostic.
     *
     * @param args optional single argument: the SonarCloud project key (defaults to StitchMl_avro)
     * @throws Exception when the SonarCloud API cannot be reached
     */
    public static void main(String[] args) throws Exception {
        String projectKey = args.length > 0 ? args[0] : "StitchMl_avro";
        String token = new SonarTokenLoader().load(SonarDiag.class);
        System.out.println("=== SonarDiag ===");
        System.out.println("projectKey : " + projectKey);
        System.out.println("token      : " + (token != null ? "present (" + token.length() + " chars)" : "MISSING"));

        SonarClient client = new SonarClient(token);
        List<SonarAnalysis> analyses = client.fetchAnalyses(projectKey);
        System.out.println("analyses   : " + analyses.size());
        if (analyses.isEmpty()) {
            System.out.println(">> No analyses returned. If token is present -> check it has access to the project.");
            return;
        }
        System.out.println("first date : " + analyses.get(0).date() + "  version=" + analyses.get(0).projectVersion());
        System.out.println("last  date : " + analyses.get(analyses.size() - 1).date()
                + "  version=" + analyses.get(analyses.size() - 1).projectVersion());

        SonarAnalysis earliest = analyses.get(0);
        Map<String, Integer> smells = client.fetchFileSmells(projectKey, earliest.key());
        System.out.println("earliest analysis files-with-smells (normalized paths): " + smells.size());
        smells.entrySet().stream().limit(12)
                .forEach(e -> System.out.println("  " + e.getKey() + " = " + e.getValue()));
        System.out.println(">> Compare the paths above with the dataset Path column "
                + "(e.g. lang/java/src/java/org/apache/avro/...). If prefixes differ, it is a path mismatch.");
    }
}
