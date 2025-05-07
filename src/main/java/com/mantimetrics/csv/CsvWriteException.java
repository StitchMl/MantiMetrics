package com.mantimetrics.csv;

/**
 * Thrown when the CSV export cannot be completed.
 */
public class CsvWriteException extends Exception {
    public CsvWriteException(String message, Throwable cause) {
        super(message, cause);
    }
}