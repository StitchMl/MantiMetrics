package com.mantimetrics.sonar;

import java.time.Instant;

/**
 * Represents a single SonarCloud analysis snapshot with its stable key and creation date.
 *
 * @param key unique analysis identifier used to retrieve file-level measures
 * @param date timestamp when the analysis was performed
 */
public record SonarAnalysis(String key, Instant date) {
}
