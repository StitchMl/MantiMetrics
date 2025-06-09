package com.mantimetrics.git;

import org.eclipse.jgit.api.errors.GitAPIException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/** Cache (condivisa) dei cloni bare/shallow dei repository GitHub. */
public class LocalRepoCacheManager {
    private static final Map<String, LocalRepoCache> cache = new ConcurrentHashMap<>();

    private LocalRepoCacheManager() {
        // Prevent instantiation
    }

    /**
     * Returns the bare cached clone of the indicated repository,
     * creating it if absent.
     */
    public static LocalRepoCache obtain(String owner, String repo) {

        String url = "https://github.com/" + owner + "/" + repo + ".git";
        return cache.computeIfAbsent(url, u -> {
                try {
                    Path workDir = Files.createTempDirectory("manti-local-" + repo);
                    return new LocalRepoCache(u, workDir);
                } catch (GitAPIException | IOException e) {
                    throw new LocalRepoCacheException("Failed to create local cache for " + owner + '/' + repo, e);
                }
        });
    }
}