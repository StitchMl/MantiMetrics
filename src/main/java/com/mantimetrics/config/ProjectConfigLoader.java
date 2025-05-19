package com.mantimetrics.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mantimetrics.git.ProjectConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;

/**
 * Load from resources/projects-config.json
 * the array of ProjectConfig.
 */
public final class ProjectConfigLoader {

    private static final Logger logger = LoggerFactory.getLogger(ProjectConfigLoader.class);
    private static final String CONFIG_PATH = System.getProperty("mantimetrics.config.path", "/projects-config.json");
    private static final ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    /**
     * Private constructor to prevent instantiation.
     */
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
        logger.info("Loading project configuration from '{}'", CONFIG_PATH);
        try (InputStream in = ProjectConfigLoader.class.getResourceAsStream(CONFIG_PATH)) {
            if (in == null) {
                logger.error("Configuration not found at '{}'", CONFIG_PATH);
                throw new ConfigurationException("Configuration not found: " + CONFIG_PATH);
            }
            ProjectConfig[] configs = mapper.readValue(in, ProjectConfig[].class);
            logger.info("Configuration successfully loaded: {} projects found", configs.length);
            return configs;
        } catch (IOException e) {
            throw new ConfigurationException("Failed to load configuration from " + CONFIG_PATH, e);
        }
    }
}