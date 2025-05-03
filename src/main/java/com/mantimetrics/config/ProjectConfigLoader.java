package com.mantimetrics.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mantimetrics.git.ProjectConfig;

import java.io.InputStream;

/**
 * Load from resources/projects-config.json
 * the array of ProjectConfig.
 */
public class ProjectConfigLoader {

    private static final String CONFIG_PATH = "/projects-config.json";

    public static ProjectConfig[] load() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        try (InputStream in = ProjectConfigLoader.class.getResourceAsStream(CONFIG_PATH)) {
            if (in == null) {
                throw new IllegalStateException("Configuration not found: " + CONFIG_PATH);
            }
            return mapper.readValue(in, ProjectConfig[].class);
        }
    }
}