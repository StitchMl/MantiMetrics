package com.mantimetrics.release;

public class ReleaseProcessingException extends RuntimeException {
    public ReleaseProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}