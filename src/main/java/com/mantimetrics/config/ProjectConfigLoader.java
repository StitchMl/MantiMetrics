package com.mantimetrics.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mantimetrics.git.ProjectConfig;

import java.io.InputStream;

/**
 * Load from resources/projects-config.json
 * the array of ProjectConfig.
 */
public final class ProjectConfigLoader {

    private static final String CONFIG_PATH = "/projects-config.json";

    private ProjectConfigLoader() {
        throw new AssertionError("Do not instantiate ProjectConfigLoader");
    }

    /**
     * Loads the ProjectConfig array from JSON.
     *
     * @return array of ProjectConfig
     * @throws ConfigurationException if the file is not found or there is a parsing error
     */
    public static ProjectConfig[] load() throws ConfigurationException {
        ObjectMapper mapper = new ObjectMapper();
        try (InputStream in = ProjectConfigLoader.class.getResourceAsStream(CONFIG_PATH)) {
            if (in == null) {
                throw new ConfigurationException("Configuration not found: " + CONFIG_PATH);
            }
            return mapper.readValue(in, ProjectConfig[].class);
        } catch (ConfigurationException e) {
            // raises our specific exception
            throw e;
        } catch (Exception e) {
            // wrap any other exception in ConfigurationException
            throw new ConfigurationException("Failed to load configuration from " + CONFIG_PATH, e);
        }
    }
}