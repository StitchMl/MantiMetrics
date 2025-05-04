package com.mantimetrics.git;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents the configuration of a Git+JIRA project.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProjectConfig {

    private final String owner;
    private final String name;
    private final String repoUrl;
    private final String jiraProjectKey;
    private final String localPath;

    /**
     * Jackson uses this constructor to create instances from JSON.
     *
     * @param name project name
     * @param repoUrl URL of the Git repo
     * @param jiraProjectKey project key in JIRA
     */
    @JsonCreator
    public ProjectConfig(
            @JsonProperty("owner") String owner,
            @JsonProperty("name") String name,
            @JsonProperty("repoUrl") String repoUrl,
            @JsonProperty("jiraKey") String jiraProjectKey
    ) {
        this.owner = owner;
        this.name = name;
        this.repoUrl = repoUrl;
        this.jiraProjectKey = jiraProjectKey;
        this.localPath = "repos/" + name.toLowerCase();
    }

    public String getOwner() {
        return owner;
    }

    public String getName() {
        return name;
    }

    public String getRepoUrl() {
        return repoUrl;
    }

    public String getJiraProjectKey() {
        return jiraProjectKey;
    }

    public String getLocalPath() {
        return localPath;
    }
}
