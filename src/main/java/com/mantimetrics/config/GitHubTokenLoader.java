package com.mantimetrics.config;

import java.io.IOException;
import java.util.Properties;

/**
 * Loads the GitHub personal access token used by the GitHub API clients.
 * Values can come from bundled configuration, optional override files or runtime overrides.
 */
public final class GitHubTokenLoader {
    private final PropertiesLoaderSupport propertiesLoaderSupport;

    /**
     * Creates a loader using the default properties helper.
     */
    public GitHubTokenLoader() {
        this(new PropertiesLoaderSupport());
    }

    /**
     * Creates a loader with an injectable helper, mainly for testing.
     *
     * @param propertiesLoaderSupport support component used to resolve property sources
     */
    GitHubTokenLoader(PropertiesLoaderSupport propertiesLoaderSupport) {
        this.propertiesLoaderSupport = propertiesLoaderSupport;
    }

    /**
     * Resolves the GitHub token from configuration files, system properties or environment variables.
     *
     * @param resourceOwner class used as the base for classpath resource loading
     * @return non-blank GitHub token
     * @throws IOException when the configuration cannot be loaded or no token is configured
     */
    public String load(Class<?> resourceOwner) throws IOException {
        Properties properties = propertiesLoaderSupport.loadResourceOrFile(
                resourceOwner,
                configPath(),
                "GitHub configuration not found"
        );
        propertiesLoaderSupport.mergeOptionalFile(properties, overridePath());
        propertiesLoaderSupport.overrideWithSystemOrEnv(
                properties,
                "github.pat",
                "mantimetrics.github.pat",
                "MANTIMETRICS_GITHUB_PAT"
        );
        return propertiesLoaderSupport.requireNonBlank(
                properties,
                "github.pat",
                "GitHub token not configured. Use config/github.local.properties, -Dmantimetrics.github.pat or MANTIMETRICS_GITHUB_PAT"
        );
    }

    /**
     * Returns the primary configuration path for GitHub settings.
     *
     * @return classpath resource path or file path containing the base GitHub configuration
     */
    private String configPath() {
        return System.getProperty("mantimetrics.github.config.path", "/github.properties");
    }

    /**
     * Returns the optional override file path for local GitHub settings.
     *
     * @return optional local override file path
     */
    private String overridePath() {
        return System.getProperty("mantimetrics.github.override.path", "config/github.local.properties");
    }
}
