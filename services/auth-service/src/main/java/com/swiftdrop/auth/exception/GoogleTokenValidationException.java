package com.swiftdrop.auth.exception;

public class GoogleTokenValidationException extends RuntimeException {
    public GoogleTokenValidationException(String message) {
        super(message);
    }

    public GoogleTokenValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
