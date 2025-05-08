package com.mantimetrics.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mantimetrics.git.ProjectConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;

/**
 * Load from resources/projects-config.json
 * the array of ProjectConfig.
 */
public final class ProjectConfigLoader {

    private static final Logger logger = LoggerFactory.getLogger(ProjectConfigLoader.class);
    private static final String CONFIG_PATH = "/projects-config.json";

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
        ObjectMapper mapper = new ObjectMapper();
        logger.info("Loading project configuration from '{}'", CONFIG_PATH);

        try (InputStream in = ProjectConfigLoader.class.getResourceAsStream(CONFIG_PATH)) {
            if (in == null) {
                throw new ConfigurationException("Configuration not found: " + CONFIG_PATH);
            }
            ProjectConfig[] configs = mapper.readValue(in, ProjectConfig[].class);
            logger.info("Configuration successfully loaded: {} projects found", configs.length);
            return configs;

        } catch (ConfigurationException e) {
            // already logged in, I raise
            throw e;

        } catch (Exception e) {
            throw new ConfigurationException("Failed to load configuration from " + CONFIG_PATH, e);
        }
    }
}