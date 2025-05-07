package com.mantimetrics.git;

/** Thrown when the build of the file→JIRA-keys map fails. */
public class FileKeyMappingException extends Exception {
    public FileKeyMappingException(String message, Throwable cause) {
        super(message, cause);
    }
}