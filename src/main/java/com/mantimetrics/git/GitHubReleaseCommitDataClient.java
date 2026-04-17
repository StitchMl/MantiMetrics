package com.mantimetrics.git;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds release-level commit aggregates by combining commit ranges with per-commit details.
 */
final class GitHubReleaseCommitDataClient {
    private final GitHubCommitRangeClient rangeClient;
    private final GitHubCommitDetailsClient detailsClient;

    /**
     * Creates a release-commit-data client backed by the shared GitHub API client.
     *
     * @param apiClient low-level GitHub API client
     */
    GitHubReleaseCommitDataClient(GitApiClient apiClient) {
        this.rangeClient = new GitHubCommitRangeClient(apiClient);
        this.detailsClient = new GitHubCommitDetailsClient(apiClient);
    }

    /**
     * Builds the aggregate commit data for a release range.
     *
     * @param owner repository owner
     * @param repo repository name
     * @param prevTag previous release tag, or {@code null} for the first release
     * @param tag current release tag
     * @return aggregated release commit data
     * @throws IOException when GitHub data cannot be fetched
     * @throws InterruptedException when the thread is interrupted while waiting for the API
     */
    ReleaseCommitData build(String owner, String repo, String prevTag, String tag)
            throws IOException, InterruptedException {
        List<String> shas = rangeClient.listCommitShas(owner, repo, prevTag, tag);
        List<ReleaseCommitDataBuilder.ReleaseCommitSnapshot> snapshots = new ArrayList<>(shas.size());
        for (String sha : shas) {
            snapshots.add(detailsClient.fetch(owner, repo, sha));
        }
        return ReleaseCommitDataBuilder.aggregate(snapshots);
    }
}
