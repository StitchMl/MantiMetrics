package com.mantimetrics.config;

import java.io.IOException;
import java.util.Properties;

/**
 * Loads the SonarCloud authentication token used by SonarCloud clients.
 *
 * <p>Resolution order (first non-blank wins):
 * <ol>
 * <li>{@code config/sonar.local.properties} — local override file (gitignored)</li>
 * <li>{@code -Dmantimetrics.sonar.token} — JVM system property</li>
 * <li>{@code SONAR_TOKEN} — environment variable</li>
 * <li>{@code sonar.properties} — classpath resource (empty by default)</li>
 * </ol>
 */
public final class SonarTokenLoader {
 private final PropertiesLoaderSupport support;

 /**
 * Creates a loader using the default properties helper.
 */
 public SonarTokenLoader() {
 this(new PropertiesLoaderSupport());
 }

 /**
 * Creates a loader with an injectable helper, mainly for testing.
 *
 * @param support support component used to resolve property sources
 */
 SonarTokenLoader(PropertiesLoaderSupport support) {
 this.support = support;
 }

 /**
 * Resolves the SonarCloud token, returning {@code null} when none is configured
 * (SonarCloud public projects work without authentication).
 *
 * @param resourceOwner class used as the base for classpath resource loading
 * @return token string, or {@code null} when not configured
 */
 public String load(Class<?> resourceOwner) {
 try {
 Properties properties = support.loadResourceOrFile(
 resourceOwner, "/sonar.properties", "Sonar configuration not found");
 support.mergeOptionalFile(properties, "config/sonar.local.properties");
 support.overrideWithSystemOrEnv(
 properties, "sonar.token", "mantimetrics.sonar.token", "SONAR_TOKEN");
 String token = properties.getProperty("sonar.token", "").trim();
 return token.isBlank() ? null : token;
 } catch (IOException e) {
 return null; // missing config is fine — public projects need no token
 }
 }
}
