package it.mantimetrics.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class MetricsLogger {
    private static final Logger logger = LoggerFactory.getLogger(MetricsLogger.class);

    /**
     * Reads the chosen metrics (static and commit) from the configuration
     * and logs them at the information level.
     *
     * @param config The configuration map loaded from the properties file.
     */
    public static void logChosenMetrics(Map<String, String> config) {
        String staticMetrics = config.get("metrics.static");
        String commitMetrics = config.get("metrics.commit");

        if (staticMetrics == null || staticMetrics.isEmpty()) {
            logger.warn("No static metrics were specified in the configuration.");
        } else {
            logger.info("Chosen Static Metrics: {}", staticMetrics);
        }

        if (commitMetrics == null || commitMetrics.isEmpty()) {
            logger.warn("No commit metrics were specified in the configuration.");
        } else {
            logger.info("Commit Metrics Chosen: {}", commitMetrics);
        }
    }
}