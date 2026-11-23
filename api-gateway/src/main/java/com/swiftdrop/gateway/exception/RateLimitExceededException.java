package com.swiftdrop.gateway.exception;

public class RateLimitExceededException extends RuntimeException {

    private final int limit;

    public RateLimitExceededException(String message, int limit) {
        super(message);
        this.limit = limit;
    }

    public int getLimit() {
        return limit;
    }
}
