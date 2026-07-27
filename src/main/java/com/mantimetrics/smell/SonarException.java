package com.mantimetrics.smell;

/**
 * Thrown when a SonarCloud REST call fails or returns an unexpected response.
 */
public class SonarException extends Exception {

    /**
     * Creates an exception with a descriptive message.
     *
     * @param message error description
     */
    public SonarException(String message) {
        super(message);
    }

    /**
     * Creates an exception wrapping a lower-level cause.
     *
     * @param message error description
     * @param cause underlying exception
     */
    public SonarException(String message, Throwable cause) {
        super(message, cause);
    }
}
