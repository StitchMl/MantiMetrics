package com.mantimetrics.jira;

/**
 * Generic exception for errors when calling JIRA.
 */
public class JiraClientException extends Exception {

    /**
     * Creates a new exception with a message.
     *
     * @param message human-readable error message
     */
    public JiraClientException(String message) {
        super(message);
    }

    /**
     * Creates a new exception with a message and a cause.
     *
     * @param message human-readable error message
     * @param cause original failure that caused the Jira error
     */
    public JiraClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
