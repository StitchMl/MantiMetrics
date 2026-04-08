package com.mantimetrics.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.Properties;

public final class PropertiesLoaderSupport {

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

    public String requireNonBlank(Properties properties, String propertyKey, String message) throws IOException {
        String value = properties.getProperty(propertyKey, "").trim();
        if (!hasText(value)) {
            throw new IOException(message);
        }
        return value;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
