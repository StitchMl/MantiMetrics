package com.mantimetrics.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.Properties;

/**
 * Shared helper for loading, merging and validating Java {@link Properties} sources.
 */
public final class PropertiesLoaderSupport {

    /**
     * Loads properties from a filesystem path when present, otherwise from a classpath resource.
     *
     * @param resourceOwner class used to resolve classpath resources
     * @param path filesystem or classpath location to load
     * @param missingMessage base error message used when the resource cannot be found
     * @return loaded properties
     * @throws IOException when the properties cannot be loaded
     */
    public Properties loadResourceOrFile(Class<?> resourceOwner, String path, String missingMessage) throws IOException {
        Path filePath = Paths.get(path);
        if (Files.isRegularFile(filePath)) {
            try (InputStream input = Files.newInputStream(filePath)) {
                Properties properties = new Properties();
                properties.load(input);
                return properties;
            }
        }

        String resourcePath = path.startsWith("/") ? path : "/" + path;
        try (InputStream input = resourceOwner.getResourceAsStream(resourcePath)) {
            if (input == null) {
                throw new IOException(missingMessage + ": " + path);
            }
            Properties properties = new Properties();
            properties.load(input);
            return properties;
        }
    }

    /**
     * Merges properties from an optional override file into the provided base properties.
     *
     * @param properties target properties to update
     * @param overridePath optional override file path
     * @throws IOException when the override file exists but cannot be read
     */
    public void mergeOptionalFile(Properties properties, String overridePath) throws IOException {
        if (overridePath == null || overridePath.isBlank()) {
            return;
        }

        Path path = Paths.get(overridePath);
        if (!Files.isRegularFile(path)) {
            return;
        }

        try (InputStream input = Files.newInputStream(path)) {
            Properties override = new Properties();
            override.load(input);
            override.forEach((key, value) -> properties.setProperty(
                    Objects.toString(key), Objects.toString(value)));
        }
    }

    /**
     * Overrides a property with runtime values, preferring a system property over an environment variable.
     *
     * @param properties target properties to update
     * @param propertyKey property key to override
     * @param systemPropertyName system property checked first
     * @param environmentVariableName environment variable checked when the system property is absent
     */
    public void overrideWithSystemOrEnv(
            Properties properties,
            String propertyKey,
            String systemPropertyName,
            String environmentVariableName
    ) {
        String value = System.getProperty(systemPropertyName);
        if (!hasText(value)) {
            value = System.getenv(environmentVariableName);
        }
        if (hasText(value)) {
            properties.setProperty(propertyKey, value.trim());
        }
    }

    /**
     * Returns a required non-blank property value.
     *
     * @param properties property source to inspect
     * @param propertyKey property key to resolve
     * @param message exception message used when the property is blank or missing
     * @return trimmed non-blank property value
     * @throws IOException when the property is blank or missing
     */
    public String requireNonBlank(Properties properties, String propertyKey, String message) throws IOException {
        String value = properties.getProperty(propertyKey, "").trim();
        if (!hasText(value)) {
            throw new IOException(message);
        }
        return value;
    }

    /**
     * Checks whether the supplied string contains non-blank text.
     *
     * @param value value to inspect
     * @return {@code true} when the value is not {@code null} and not blank
     */
    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
