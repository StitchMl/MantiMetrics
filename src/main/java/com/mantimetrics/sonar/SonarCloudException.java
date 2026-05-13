package com.mantimetrics.sonar;

/**
 * Thrown when a SonarCloud REST call fails or returns an unexpected response.
 */
public class SonarCloudException extends Exception {

 /**
 * Creates an exception with a descriptive message.
 *
 * @param message error description
 */
 public SonarCloudException(String message) {
 super(message);
 }

 /**
 * Creates an exception wrapping a lower-level cause.
 *
 * @param message error description
 * @param cause underlying exception
 */
 public SonarCloudException(String message, Throwable cause) {
 super(message, cause);
 }
}
