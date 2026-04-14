package com.mantimetrics.git;

import com.mantimetrics.parser.SourceScanResult;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Small facade over the GitHub-specific clients used by the pipeline.
 */
public final class GitService {
    private final GitHubRepositoryClient repositoryClient;
    private final GitHubReleaseCommitDataClient releaseCommitDataClient;
    private final ZipDownloader zipDownloader;

    public GitService(String token) {
        GitApiClient apiClient = new GitApiClient(token);
        this.repositoryClient = new GitHubRepositoryClient(apiClient);
        this.releaseCommitDataClient = new GitHubReleaseCommitDataClient(apiClient);
        this.zipDownloader = new ZipDownloader(apiClient);
    }

    @SuppressWarnings("unused")
    public String getDefaultBranch(String owner, String repo) {
        return repositoryClient.getDefaultBranch(owner, repo);
    }

    public List<String> listTags(String owner, String repo) {
        return repositoryClient.listTags(owner, repo);
    }

    public SourceScanResult downloadReleaseSources(String owner, String repo, String ref)
            throws IOException, InterruptedException {
        return zipDownloader.downloadSources(owner, repo, ref);
    }

    public List<Path> getTmp() {
        return zipDownloader.getTmpDirs();
    }

    public int compareTagDates(String owner, String repo, String tagA, String tagB) {
        return repositoryClient.compareTagDates(owner, repo, tagA, tagB);
    }

    public ReleaseCommitData buildReleaseCommitData(
            String owner,
            String repo,
            String prevTag,
            String tag
    ) throws IOException, InterruptedException {
        return releaseCommitDataClient.build(owner, repo, prevTag, tag);
    }
}
