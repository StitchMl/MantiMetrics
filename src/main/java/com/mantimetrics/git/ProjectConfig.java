package com.mantimetrics.git;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents the configuration of a Git+JIRA project.
 *
 * @param owner repository owner
 * @param name repository name
 * @param repoUrl optional repository URL used to derive owner and name when absent
 * @param percentage percentage of releases to analyze
 * @param jiraProjectKey Jira project key associated with the repository
 * @param sonarProjectKey SonarCloud project key; {@code null} when SonarCloud is not configured
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ProjectConfig(String owner, String name, String repoUrl, Integer percentage,
 String jiraProjectKey, String sonarProjectKey) {

 /**
 * Creates a project configuration while resolving owner and repository name from the URL when needed.
 *
 * @param owner repository owner, possibly blank when {@code repoUrl} is provided
 * @param name repository name, possibly blank when {@code repoUrl} is provided
 * @param repoUrl optional repository URL used to derive owner and name
 * @param percentage optional percentage of releases to analyze
 * @param jiraProjectKey Jira project key associated with the repository
 * @param sonarProjectKey SonarCloud project key; may be {@code null}
 */
 @JsonCreator
 public ProjectConfig(
 @JsonProperty("owner") String owner,
 @JsonProperty("name") String name,
 @JsonProperty("repoUrl") String repoUrl,
 @JsonProperty("percentage") Integer percentage,
 @JsonProperty("jiraKey") String jiraProjectKey,
 @JsonProperty("sonarKey") String sonarProjectKey
 ) {
 RepositoryCoordinates coordinates = RepositoryUrlParser.resolve(owner, name, repoUrl);
 this.owner = coordinates.owner();
 this.name = coordinates.name();
 this.repoUrl = repoUrl;
 this.percentage = percentage;
 this.jiraProjectKey = jiraProjectKey;
 this.sonarProjectKey = sonarProjectKey;
 }

 /**
 * Creates a project configuration without an explicit repository URL or SonarCloud key.
 *
 * @param owner repository owner
 * @param name repository name
 * @param percentage optional percentage of releases to analyze
 * @param jiraProjectKey Jira project key associated with the repository
 */
 public ProjectConfig(String owner, String name, Integer percentage, String jiraProjectKey) {
 this(owner, name, null, percentage, jiraProjectKey, null);
 }

 /**
 * Returns the configured release percentage, defaulting to {@code 100} when omitted.
 *
 * @return release percentage to analyze
 */
 @Override
 public Integer percentage() {
 return percentage == null ? 100 : percentage;
 }
}
