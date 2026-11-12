package com.swiftdrop.merchant.security;

/** Short-lived issuance value. Never log or serialize this object. */
public final class GeneratedApiKey {
    private final String publicId;
    private final String plaintext;
    public GeneratedApiKey(String publicId, String plaintext) { this.publicId = publicId; this.plaintext = plaintext; }
    public String publicId() { return publicId; }
    public String plaintext() { return plaintext; }
    @Override public String toString() { return "GeneratedApiKey[REDACTED]"; }
}
