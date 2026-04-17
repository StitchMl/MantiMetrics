package com.mantimetrics.parser;

/**
 * Exception thrown by CodeParser in case of I/O problems,
 * parsing or generics during remote code analysis.
 */
public class CodeParserException extends Exception {

    /**
     * Constructs a new CodeParserException with the specified detail message.
     *
     * @param message the detail message
     * @param cause original failure that caused the parser exception
     */
    public CodeParserException(String message, Throwable cause) {
        super(message, cause);
    }
}
