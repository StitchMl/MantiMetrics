package com.mantimetrics.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mantimetrics.git.GitConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;

/**
 * Load from resources/projects-config.json
 * the array of GitConfig.
 */
public final class ConfigLoader {

    private static final Logger logger = LoggerFactory.getLogger(ConfigLoader.class);
    private static final String CONFIG_PATH = System.getProperty("mantimetrics.config.path", "/projects-config.json");
    private static final ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    /**
     * Private constructor to prevent instantiation.
     */
    private ConfigLoader() {
        throw new AssertionError("Do not instantiate ConfigLoader");
    }

    /**
     * Loads the GitConfig array from JSON.
     *
     * @return array of GitConfig
     * @throws ConfigException if the file is not found or there is a parsing error
     */
    public static GitConfig[] load() throws ConfigException {
        logger.info("Loading project configuration from '{}'", CONFIG_PATH);
        try (InputStream in = ConfigLoader.class.getResourceAsStream(CONFIG_PATH)) {
            if (in == null) {
                logger.error("Configuration not found at '{}'", CONFIG_PATH);
                throw new ConfigException("Configuration not found: " + CONFIG_PATH);
            }
            GitConfig[] configs = mapper.readValue(in, GitConfig[].class);
            logger.info("Configuration successfully loaded: {} projects found", configs.length);
            return configs;
        } catch (IOException e) {
            throw new ConfigException("Failed to load configuration from " + CONFIG_PATH, e);
        }
    }
}