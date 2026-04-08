package com.mantimetrics.git;

import java.net.URI;
import java.net.URISyntaxException;

final class RepositoryUrlParser {

    private RepositoryUrlParser() {
        throw new AssertionError("Do not instantiate RepositoryUrlParser");
    }

    static RepositoryCoordinates resolve(String owner, String name, String repoUrl) {
        if (hasText(owner) && hasText(name)) {
            return new RepositoryCoordinates(owner.trim(), name.trim());
        }
        if (!hasText(repoUrl)) {
            throw new IllegalArgumentException("Either owner/name or repoUrl must be configured");
        }

        RepositoryCoordinates parsed = parse(repoUrl.trim());
        String resolvedOwner = hasText(owner) ? owner.trim() : parsed.owner();
        String resolvedName = hasText(name) ? name.trim() : parsed.name();
        return new RepositoryCoordinates(resolvedOwner, resolvedName);
    }

    static RepositoryCoordinates parse(String repoUrl) {
        if (repoUrl.startsWith("git@")) {
            return parseScpLikeUrl(repoUrl);
        }
        try {
            URI uri = new URI(repoUrl);
            return parsePath(uri.getPath(), repoUrl);
        } catch (URISyntaxException exception) {
            throw new IllegalArgumentException("Invalid repository URL: " + repoUrl, exception);
        }
    }

    private static RepositoryCoordinates parseScpLikeUrl(String repoUrl) {
        int colon = repoUrl.indexOf(':');
        if (colon < 0 || colon == repoUrl.length() - 1) {
            throw new IllegalArgumentException("Invalid SCP-like repository URL: " + repoUrl);
        }
        return parsePath(repoUrl.substring(colon + 1), repoUrl);
    }

    private static RepositoryCoordinates parsePath(String rawPath, String repoUrl) {
        if (!hasText(rawPath)) {
            throw new IllegalArgumentException("Repository URL does not contain a path: " + repoUrl);
        }

        String[] parts = rawPath.replace('\\', '/').split("/");
        String owner = null;
        String repo = null;
        for (String part : parts) {
            if (part == null || part.isBlank()) {
                continue;
            }
            if (owner == null) {
                owner = part;
            } else {
                repo = stripGitSuffix(part);
                break;
            }
        }

        if (!hasText(owner) || !hasText(repo)) {
            throw new IllegalArgumentException("Cannot extract owner/repository from URL: " + repoUrl);
        }
        return new RepositoryCoordinates(owner, repo);
    }

    private static String stripGitSuffix(String value) {
        return value.endsWith(".git") ? value.substring(0, value.length() - 4) : value;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
