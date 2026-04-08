package com.mantimetrics.config;

import java.io.IOException;
import java.util.Properties;

public final class GitHubTokenLoader {
    private final PropertiesLoaderSupport propertiesLoaderSupport;

    public GitHubTokenLoader() {
        this(new PropertiesLoaderSupport());
    }

    GitHubTokenLoader(PropertiesLoaderSupport propertiesLoaderSupport) {
        this.propertiesLoaderSupport = propertiesLoaderSupport;
    }

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

    private String configPath() {
        return System.getProperty("mantimetrics.github.config.path", "/github.properties");
    }

    private String overridePath() {
        return System.getProperty("mantimetrics.github.override.path", "config/github.local.properties");
    }
}
