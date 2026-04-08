package com.mantimetrics.git;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

final class GitHubReleaseCommitDataClient {
    private final GitHubCommitRangeClient rangeClient;
    private final GitHubCommitDetailsClient detailsClient;

    GitHubReleaseCommitDataClient(GitApiClient apiClient) {
        this.rangeClient = new GitHubCommitRangeClient(apiClient);
        this.detailsClient = new GitHubCommitDetailsClient(apiClient);
    }

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
