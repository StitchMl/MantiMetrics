package com.mantimetrics.parser;

/**
 * Exception thrown by CodeParser in case of I/O problems,
 * parsing or generics during remote code analysis.
 */
public class CodeParserException extends Exception {
    public CodeParserException(String message) {
        super(message);
    }

    public CodeParserException(String message, Throwable cause) {
        super(message, cause);
    }
}