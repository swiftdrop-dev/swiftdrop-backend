package com.swiftdrop.dispatch.security;

public final class IdentityMappingUnavailableException extends RuntimeException {
    public IdentityMappingUnavailableException() {
        super("A trusted dispatch identity mapping is not configured");
    }
}
