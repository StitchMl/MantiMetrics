package com.mantimetrics.git;

/** Thrown when the build of the file→JIRA-keys map fails. */
public class FileKeyMappingException extends Exception {

 /** Create a new exception with the given message. */
 public FileKeyMappingException(String message, Throwable cause) {
 super(message, cause);
 }
}