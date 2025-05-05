package com.mantimetrics.jira;

/**
 * Generic exception for errors when calling JIRA.
 */
public class JiraClientException extends Exception {
    public JiraClientException(String message) {
        super(message);
    }
    public JiraClientException(String message, Throwable cause) {
        super(message, cause);
    }
}