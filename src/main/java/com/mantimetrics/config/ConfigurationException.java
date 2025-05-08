package com.mantimetrics.config;

/**
 * Exception thrown if the configuration cannot be loaded.
 */
public class ConfigurationException extends Exception {

    /**
     * Creates a new ConfigurationException with the specified message.
     *
     * @param message the detail message
     */
    public ConfigurationException(String message) {
        super(message);
    }

    /**
     * Creates a new ConfigurationException with the specified message and cause.
     *
     * @param message the detail message
     * @param cause   the cause of the exception
     */
    public ConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}