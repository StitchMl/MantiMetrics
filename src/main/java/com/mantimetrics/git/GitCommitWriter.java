package com.mantimetrics.git;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds release-level commit aggregates by combining commit ranges with per-commit details.
 */
final class GitCommitWriter {
    private final GitCommitRangeTaker rangeClient;
    private final GitCommitDetails detailsClient;

    /**
     * Creates a release-commit-data client backed by the shared GitHub API client.
     *
     * @param apiClient low-level GitHub API client
     */
    GitCommitWriter(GitClient apiClient) {
        this.rangeClient = new GitCommitRangeTaker(apiClient);
        this.detailsClient = new GitCommitDetails(apiClient);
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
    GitReleaseSnapshot build(String owner, String repo, String prevTag, String tag)
            throws IOException, InterruptedException {
        return build(owner, repo, prevTag, tag, false);
    }

    GitReleaseSnapshot build(String owner, String repo, String prevTag, String tag, boolean includeGithub)
            throws IOException, InterruptedException {
        return GitPrevReleaseBuilder.aggregate(fetchRaw(owner, repo, prevTag, tag).commits, includeGithub);
    }

    /**
     * Fetches the raw commit snapshots for a release range without aggregating them, so the
     * aggregation can later be recomputed per dataset variant.
     *
     * @param owner repository owner
     * @param repo repository name
     * @param prevTag previous release tag, or {@code null} for the first release
     * @param tag current release tag
     * @return opaque holder of the raw commit snapshots
     * @throws IOException when GitHub data cannot be fetched
     * @throws InterruptedException when the thread is interrupted while waiting for the API
     */
    RawReleaseCommits fetchRaw(String owner, String repo, String prevTag, String tag)
            throws IOException, InterruptedException {
        List<String> shas = rangeClient.listCommitShas(owner, repo, prevTag, tag);
        List<GitPrevReleaseBuilder.ReleaseCommitSnapshot> snapshots = new ArrayList<>(shas.size());
        for (String sha : shas) {
            snapshots.add(detailsClient.fetch(owner, repo, sha));
        }
        return new RawReleaseCommits(snapshots);
    }

    /**
     * Aggregates previously fetched raw commits with the requested issue-key source.
     *
     * @param raw raw commit snapshots
     * @param includeGithub whether to also link GitHub issue references
     * @return aggregated release commit data
     */
    static GitReleaseSnapshot aggregate(RawReleaseCommits raw, boolean includeGithub) {
        return GitPrevReleaseBuilder.aggregate(raw.commits, includeGithub);
    }
}
