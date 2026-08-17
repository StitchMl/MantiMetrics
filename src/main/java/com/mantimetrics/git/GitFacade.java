package com.mantimetrics.git;

import com.mantimetrics.javaparsing.ScanResult;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

/**
 * Small facade over the GitHub-specific clients used by the pipeline.
 */
public final class GitFacade {
    private final GitRepoClient repositoryClient;
    private final GitCommitWriter releaseCommitDataClient;
    private final GitZipDownloader zipDownloader;

    /**
     * Creates the Git service and all GitHub-backed collaborators sharing the same token.
     *
     * @param token GitHub personal access token
     */
    public GitFacade(String token) {
        GitClient apiClient = new GitClient(token);
        this.repositoryClient = new GitRepoClient(apiClient);
        this.releaseCommitDataClient = new GitCommitWriter(apiClient);
        this.zipDownloader = new GitZipDownloader(apiClient);
    }

    /**
     * Returns the default branch of a repository.
     *
     * @param owner repository owner
     * @param repo repository name
     * @return default branch name
     */
    @SuppressWarnings("unused")
    public String getDefaultBranch(String owner, String repo) {
        return repositoryClient.getDefaultBranch(owner, repo);
    }

    /**
     * Lists the tags published by a repository.
     *
     * @param owner repository owner
     * @param repo repository name
     * @return repository tags
     */
    public List<String> listTags(String owner, String repo) {
        return repositoryClient.listTags(owner, repo);
    }

    /**
     * Returns the commit date associated with a tag, using the cached value when available.
     *
     * @param owner repository owner
     * @param repo repository name
     * @param tag tag to inspect
     * @return commit date of the tag
     */
    public Instant getTagDate(String owner, String repo, String tag) {
        return repositoryClient.fetchTagDate(owner, repo, tag);
    }

    /**
     * Downloads and extracts the Java production sources for a release reference.
     *
     * @param owner repository owner
     * @param repo repository name
     * @param ref tag or branch reference to download
     * @return extracted source files for the release
     * @throws IOException when the download or extraction fails
     * @throws InterruptedException when the thread is interrupted while waiting for the download
     */
    public ScanResult downloadReleaseSources(String owner, String repo, String ref)
            throws IOException, InterruptedException {
        return zipDownloader.downloadSources(owner, repo, ref);
    }

    /**
     * Downloads the full release ZIP and extracts all files to {@code targetDir}.
     * The top-level archive folder is stripped so that {@code pom.xml} lands directly
     * inside {@code targetDir}.
     *
     * @param owner     repository owner
     * @param repo      repository name
     * @param ref       tag or branch reference to download
     * @param targetDir directory that will receive the extracted content (must already exist)
     * @throws IOException          when the download or extraction fails
     * @throws InterruptedException when the thread is interrupted while waiting for the download
     */
    public void extractReleaseFull(String owner, String repo, String ref, Path targetDir)
            throws IOException, InterruptedException {
        zipDownloader.extractFullRelease(owner, repo, ref, targetDir);
    }

    /**
     * Returns temporary directories created by the download layer.
     *
     * @return temporary directories to clean up
     */
    public List<Path> getTmp() {
        return zipDownloader.getTmpDirs();
    }

    /**
     * Compares two tags according to their commit date.
     *
     * @param owner repository owner
     * @param repo repository name
     * @param tagA first tag
     * @param tagB second tag
     * @return negative, zero or positive depending on chronological order
     */
    public int compareTagDates(String owner, String repo, String tagA, String tagB) {
        return repositoryClient.compareTagDates(owner, repo, tagA, tagB);
    }

    /**
     * Builds aggregated commit data for the release range between two tags.
     *
     * @param owner repository owner
     * @param repo repository name
     * @param prevTag previous release tag, or {@code null} for the first release
     * @param tag current release tag
     * @return aggregated commit data for the release range
     * @throws IOException when GitHub data cannot be fetched
     * @throws InterruptedException when the thread is interrupted while waiting for the API
     */
    public GitReleaseSnapshot buildReleaseCommitData(
            String owner,
            String repo,
            String prevTag,
            String tag
    ) throws IOException, InterruptedException {
        return buildReleaseCommitData(owner, repo, prevTag, tag, false);
    }

    /**
     * Aggregates commit data for a release range, optionally linking GitHub issue references.
     *
     * @param owner repository owner
     * @param repo repository name
     * @param prevTag previous release tag, or {@code null} for the first release
     * @param tag current release tag
     * @param includeGithub whether to also link GitHub issue references
     * @return aggregated commit data for the release range
     * @throws IOException when GitHub data cannot be fetched
     * @throws InterruptedException when the thread is interrupted while waiting for the API
     */
    public GitReleaseSnapshot buildReleaseCommitData(
            String owner,
            String repo,
            String prevTag,
            String tag,
            boolean includeGithub
    ) throws IOException, InterruptedException {
        return releaseCommitDataClient.build(owner, repo, prevTag, tag, includeGithub);
    }

    /**
     * Fetches the raw commit snapshots for a release range once, so the aggregation can be recomputed
     * per dataset variant (collect-once / derive-many).
     *
     * @param owner repository owner
     * @param repo repository name
     * @param prevTag previous release tag, or {@code null} for the first release
     * @param tag current release tag
     * @return opaque holder of the raw commit snapshots
     * @throws IOException when GitHub data cannot be fetched
     * @throws InterruptedException when the thread is interrupted while waiting for the API
     */
    public RawReleaseCommits fetchRawReleaseCommits(String owner, String repo, String prevTag, String tag)
            throws IOException, InterruptedException {
        return releaseCommitDataClient.fetchRaw(owner, repo, prevTag, tag);
    }

    /**
     * Aggregates previously fetched raw commits with the requested issue-key source.
     *
     * @param raw raw commit snapshots
     * @param includeGithub whether to also link GitHub issue references
     * @return aggregated release commit data
     */
    public GitReleaseSnapshot aggregate(RawReleaseCommits raw, boolean includeGithub) {
        return GitCommitWriter.aggregate(raw, includeGithub);
    }
}
