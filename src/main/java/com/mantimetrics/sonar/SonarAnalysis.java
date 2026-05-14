package com.mantimetrics.sonar;

import java.time.Instant;

/**
 * Represents a single SonarCloud analysis snapshot.
 *
 * @param key unique analysis identifier used to retrieve file-level measures
 * @param date timestamp when the analysis was performed
 * @param projectVersion version label attached to the analysis by the scanner
 * (set via {@code sonar.projectVersion}); may be {@code null}
 */
public record SonarAnalysis(String key, Instant date, String projectVersion) {
}
