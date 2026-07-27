package com.mantimetrics.config;

/**
 * Exception thrown if the configuration cannot be loaded.
 */
public class ConfigException extends Exception {

    /**
     * Creates a new ConfigException with the specified message.
     *
     * @param message the detail message
     */
    public ConfigException(String message) {
        super(message);
    }

    /**
     * Creates a new ConfigException with the specified message and cause.
     *
     * @param message the detail message
     * @param cause   the cause of the exception
     */
    public ConfigException(String message, Throwable cause) {
        super(message, cause);
    }
}