package it.mantimetrics.utils;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Class that reads from the configuration the metrics to be considered (static and commit)
 * and stores them as dynamic lists.
 */
public class MetricsConfiguration {
    private List<String> staticMetrics;
    private List<String> commitMetrics;

    public MetricsConfiguration(Map<String, String> config) {
        String staticMetricsConfig = config.get("metrics.static");
        String commitMetricsConfig = config.get("metrics.commit");

        if (staticMetricsConfig != null && !staticMetricsConfig.isEmpty()) {
            staticMetrics = Arrays.stream(staticMetricsConfig.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());
        }

        if (commitMetricsConfig != null && !commitMetricsConfig.isEmpty()) {
            commitMetrics = Arrays.stream(commitMetricsConfig.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());
        }
    }

    public List<String> getStaticMetrics() {
        return staticMetrics;
    }

    public List<String> getCommitMetrics() {
        return commitMetrics;
    }
}