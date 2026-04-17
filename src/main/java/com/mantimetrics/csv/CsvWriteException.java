package com.mantimetrics.csv;

/**
 * Thrown when the CSV export cannot be completed.
 */
public class CsvWriteException extends Exception {

    /**
     * Creates a new exception describing a CSV write failure.
     *
     * @param message human-readable error message
     * @param cause original failure that prevented the CSV operation
     */
    public CsvWriteException(String message, Throwable cause) {
        super(message, cause);
    }
}
