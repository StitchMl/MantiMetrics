package com.mantimetrics.git;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents the configuration of a Git+JIRA project.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ProjectConfig(String owner, String name, String repoUrl, Integer percentage, String jiraProjectKey) {

    @JsonCreator
    public ProjectConfig(
            @JsonProperty("owner") String owner,
            @JsonProperty("name") String name,
            @JsonProperty("repoUrl") String repoUrl,
            @JsonProperty("percentage") Integer percentage,
            @JsonProperty("jiraKey") String jiraProjectKey
    ) {
        RepositoryCoordinates coordinates = RepositoryUrlParser.resolve(owner, name, repoUrl);
        this.owner = coordinates.owner();
        this.name = coordinates.name();
        this.repoUrl = repoUrl;
        this.percentage = percentage;
        this.jiraProjectKey = jiraProjectKey;
    }

    public ProjectConfig(String owner, String name, Integer percentage, String jiraProjectKey) {
        this(owner, name, null, percentage, jiraProjectKey);
    }

    @Override
    public Integer percentage() {
        return percentage == null ? 100 : percentage;
    }
}
