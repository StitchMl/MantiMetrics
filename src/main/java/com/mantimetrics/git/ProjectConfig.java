package com.mantimetrics.git;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents the configuration of a Git+JIRA project.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ProjectConfig(String owner, String name, Integer percentage, String jiraProjectKey) {

    /**
     * Jackson uses this constructor to create instances from JSON.
     *
     * @param owner          project owner
     * @param name           project name
     * @param percentage     percentage of release
     * @param jiraProjectKey project key in JIRA
     */
    @JsonCreator
    public ProjectConfig(
            @JsonProperty("owner") String owner,
            @JsonProperty("name") String name,
            @JsonProperty("percentage") Integer percentage,
            @JsonProperty("jiraKey") String jiraProjectKey
    ) {
        this.owner = owner;
        this.name = name;
        this.percentage = percentage;
        this.jiraProjectKey = jiraProjectKey;
    }

    @Override
    public Integer percentage() {
        return percentage == null ? 100 : percentage;
    }
}
