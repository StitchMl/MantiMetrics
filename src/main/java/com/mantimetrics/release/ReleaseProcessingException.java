package com.mantimetrics.release;

/**
 * Unchecked exception raised when a release cannot be processed because of infrastructure or I/O failures.
 */
public class ReleaseProcessingException extends RuntimeException {
    /**
     * Creates a new release-processing exception.
     *
     * @param message human-readable error message
     * @param cause original failure that interrupted processing
     */
    public ReleaseProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}
