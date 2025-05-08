package com.mantimetrics.csv;

/**
 * Thrown when the CSV export cannot be completed.
 */
public class CsvWriteException extends Exception {

    /**
     * Create a new exception with the given message.
     */
    public CsvWriteException(String message, Throwable cause) {
        super(message, cause);
    }
}