package com.mantimetrics.jira;

/**
 * Generic exception for errors when calling JIRA.
 */
public class JiraClientException extends Exception {

    /**
     * Create a new exception with a message.
     */
    public JiraClientException(String message) {
        super(message);
    }

    /**
     * Create a new exception with a message and a cause.
     */
    public JiraClientException(String message, Throwable cause) {
        super(message, cause);
    }
}