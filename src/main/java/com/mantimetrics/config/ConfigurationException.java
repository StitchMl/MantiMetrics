package com.mantimetrics.config;

/**
 * Exception thrown if the configuration cannot be loaded.
 */
public class ConfigurationException extends Exception {
    public ConfigurationException(String message) {
        super(message);
    }
    public ConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}