package com.mantimetrics.git;

/** Eccezione lanciata quando il clone locale di un repository fallisce. */
public class LocalRepoCacheException extends RuntimeException {
    public LocalRepoCacheException(String message, Throwable cause) {
        super(message, cause);
    }
}
