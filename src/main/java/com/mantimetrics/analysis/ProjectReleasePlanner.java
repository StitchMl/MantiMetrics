package com.mantimetrics.analysis;

import com.mantimetrics.git.GitService;
import com.mantimetrics.git.ProjectConfig;
import com.mantimetrics.jira.JiraClient;
import com.mantimetrics.jira.JiraClientException;
import com.mantimetrics.release.ReleaseSelector;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class ProjectReleasePlanner {
    private static final Logger LOG = LoggerFactory.getLogger(ProjectReleasePlanner.class);

    private final GitService gitService;
    private final ReleaseSelector releaseSelector;
    private final JiraClient jiraClient;

    public ProjectReleasePlanner(GitService gitService, ReleaseSelector releaseSelector, JiraClient jiraClient) {
        this.gitService = gitService;
        this.releaseSelector = releaseSelector;
        this.jiraClient = jiraClient;
    }

    @Nullable
    ProjectReleasePlan plan(ProjectConfig config) throws JiraClientException {
        String owner = config.owner();
        String repo = config.name().toLowerCase();

        jiraClient.initialize(config.jiraProjectKey());
        List<String> chronologicalTags = resolveChronologicalValidTags(config, owner, repo);
        if (chronologicalTags == null) {
            return null;
        }

        List<String> selectedTags = releaseSelector.selectFirstPercent(chronologicalTags, config.percentage());
        List<String> bugKeys = jiraClient.fetchBugKeys();

        LOG.info("{} - percentage {}% -> {} release to be processed",
                repo, config.percentage(), selectedTags.size());
        LOG.info("{} - processing {} tags", repo, selectedTags.size());
        LOG.info("Found {} bug keys", bugKeys.size());

        return new ProjectReleasePlan(owner, repo, selectedTags, bugKeys);
    }

    @Nullable
    private List<String> resolveChronologicalValidTags(ProjectConfig config, String owner, String repo)
            throws JiraClientException {
        List<String> gitTagsRaw = gitService.listTags(owner, repo);
        List<String> normalizedGitTags = gitTagsRaw.stream()
                .map(JiraClient::normalize)
                .toList();
        List<String> jiraVersions = jiraClient.fetchProjectVersions(config.jiraProjectKey());

        Set<String> validNormalizedTags = new HashSet<>(normalizedGitTags);
        validNormalizedTags.retainAll(jiraVersions);

        LOG.info("{} - total Git tags: {}, Jira versions: {}, common: {}",
                repo, gitTagsRaw.size(), jiraVersions.size(), validNormalizedTags.size());

        if (validNormalizedTags.isEmpty()) {
            LOG.warn("No valid release in common -> project skipped");
            return null;
        }

        return gitTagsRaw.stream()
                .filter(tag -> validNormalizedTags.contains(JiraClient.normalize(tag)))
                .sorted((left, right) -> gitService.compareTagDates(owner, repo, left, right))
                .toList();
    }
}
