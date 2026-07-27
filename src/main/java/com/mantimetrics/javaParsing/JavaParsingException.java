package com.mantimetrics.javaParsing;

/**
 * Exception thrown by JavaSourceParser in case of I/O problems,
 * parsing or generics during remote code analysis.
 */
public class JavaParsingException extends Exception {

    /**
     * Constructs a new JavaParsingException with the specified detail message.
     *
     * @param message the detail message
     * @param cause original failure that caused the parser exception
     */
    public JavaParsingException(String message, Throwable cause) {
        super(message, cause);
    }
}
