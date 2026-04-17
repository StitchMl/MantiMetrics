package com.mantimetrics.git;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * Parses repository coordinates from explicit owner/name pairs or repository URLs.
 */
final class RepositoryUrlParser {

    /**
     * Prevents instantiation of the static utility class.
     */
    private RepositoryUrlParser() {
        throw new AssertionError("Do not instantiate RepositoryUrlParser");
    }

    /**
     * Resolves repository coordinates preferring explicit owner/name values over URL-derived ones.
     *
     * @param owner explicit repository owner, possibly blank
     * @param name explicit repository name, possibly blank
     * @param repoUrl optional repository URL
     * @return resolved repository coordinates
     */
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

    /**
     * Parses repository coordinates from a repository URL.
     *
     * @param repoUrl repository URL in HTTPS or SCP-like Git syntax
     * @return parsed repository coordinates
     */
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

    /**
     * Parses repository coordinates from an SCP-like Git URL.
     *
     * @param repoUrl repository URL in {@code git@host:owner/repo.git} format
     * @return parsed repository coordinates
     */
    private static RepositoryCoordinates parseScpLikeUrl(String repoUrl) {
        int colon = repoUrl.indexOf(':');
        if (colon < 0 || colon == repoUrl.length() - 1) {
            throw new IllegalArgumentException("Invalid SCP-like repository URL: " + repoUrl);
        }
        return parsePath(repoUrl.substring(colon + 1), repoUrl);
    }

    /**
     * Extracts repository coordinates from a normalized path portion.
     *
     * @param rawPath path portion of the repository URL
     * @param repoUrl original repository URL used for error messages
     * @return parsed repository coordinates
     */
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

    /**
     * Removes the optional {@code .git} suffix from the repository name.
     *
     * @param value repository segment to normalize
     * @return normalized repository name
     */
    private static String stripGitSuffix(String value) {
        return value.endsWith(".git") ? value.substring(0, value.length() - 4) : value;
    }

    /**
     * Checks whether a string contains non-blank text.
     *
     * @param value value to inspect
     * @return {@code true} when the value is not blank
     */
    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
