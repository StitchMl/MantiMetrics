package com.mantimetrics.git;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

/** Git + GitHub REST utility – *commit-history* implementation */
public final class GitService {
    private final GitApiClient       apiClient;
    private final CommitMapper       commitMapper;
    private final ZipDownloader      zipDownloader;
    private static final String      API       = "https://api.github.com";
    private static final Logger      LOG       = LoggerFactory.getLogger(GitService.class);
    private static final String      REPOS     = "/repos/";
    private final Map<String,String> defBranch = new ConcurrentHashMap<>();

    public GitService(String token) {
        this.apiClient     = new GitApiClient(token);
        this.commitMapper  = new CommitMapper(apiClient);
        this.zipDownloader = new ZipDownloader(apiClient);
    }

    /** Returns the default branch of the given repository. */
    public String getDefaultBranch(String owner,String repo) {
        return defBranch.computeIfAbsent(owner+'/'+repo, k -> {
            try {
                return apiClient.getApi(API + REPOS + owner + '/' + repo)
                        .path("default_branch").asText("master");
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new UncheckedIOException(
                        new IOException("Interrupted while building JIRA map", ie));

            } catch (IOException ioe) {
                throw new UncheckedIOException(ioe);
            }
        });
    }

    /** Lists all tags of the given repository. */
    public List<String> listTags(String owner, String repo) throws IOException, InterruptedException {
        List<String> tags = new ArrayList<>();
        for (int page = 1; ; page++) {
            JsonNode arr = apiClient.getApi(API + REPOS + owner
                    + '/' + repo + "/tags?per_page=100&page=" + page);
            if (!arr.isArray() || arr.isEmpty()) break;
            arr.forEach(n -> Optional.ofNullable(n.path("name").asText(null))
                    .ifPresent(tags::add));
        }
        LOG.info("Found {} tags for {}/{}", tags.size(), owner, repo);
        return tags;
    }

    /** Builds a file → JIRA-keys map using the commit history of the given branch. */
    public Map<String,List<String>> getFileToIssueKeysMap(String owner, String repo, String branch)
            throws FileKeyMappingException {
        return commitMapper.getFileToIssueKeysMap(owner,repo,branch);
    }

    /**
     * Returns the list of commit hashes touching filePath
     * between fromTag (excluded) and toTag (included).
     */
    public List<String> getCommitsInRange(String owner,
                                          String repo,
                                          String filePath,
                                          String fromTag,
                                          String toTag) throws IOException, InterruptedException {
        return commitMapper.getCommitsInRange(owner, repo, filePath, fromTag, toTag);
    }

    /** download + unzip con timeout esteso e retry su SocketTimeout. */
    public Path downloadAndUnzipRepo(String owner,
                                     String repo,
                                     String ref,
                                     String subDir) throws IOException, InterruptedException {
        return zipDownloader.downloadAndUnzip(owner, repo, ref, subDir);
    }

    /** Deletes the temporary directory and all its contents. */
    public List<Path> getTmp() {
        return zipDownloader.getTmpDirs();
    }

    /**
     * Chronological comparison of two tags in the same repo.
     */
    public int compareTagDates(String owner, String repo, String tagA, String tagB) {
        try {
            Instant dateA = commitMapper.getTagDate(owner, repo, tagA);
            Instant dateB = commitMapper.getTagDate(owner, repo, tagB);
            return dateA.compareTo(dateB);
        } catch (IOException e) {
            // trasformiamo in unchecked: in genere non vogliamo far
            // fallire tutto per un problema di rete su un singolo tag
            throw new UncheckedIOException(
                    new IOException("Impossibile recuperare le date dei tag", e)
            );
        } catch (InterruptedException e){
            Thread.currentThread().interrupt();
            throw new UncheckedIOException(
                    new IOException("Impossibile recuperare le date dei tag", e)
            );
        }
    }
}