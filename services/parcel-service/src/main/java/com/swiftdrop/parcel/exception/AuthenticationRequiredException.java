package com.swiftdrop.parcel.exception;

public class AuthenticationRequiredException extends RuntimeException {
    public AuthenticationRequiredException() {
        super("Authentication required");
    }
}
