package com.swiftdrop.merchant.security;
import java.security.SecureRandom;
import java.util.Base64;

public final class ApiKeyGenerator {
    private final SecureRandom random = new SecureRandom();
    public GeneratedApiKey generate() {
        byte[] id = new byte[16]; byte[] secret = new byte[32];
        random.nextBytes(id); random.nextBytes(secret);
        var encoder = Base64.getUrlEncoder().withoutPadding();
        String publicId = encoder.encodeToString(id);
        return new GeneratedApiKey(publicId, "sdm_v1_" + publicId + "." + encoder.encodeToString(secret));
    }
}
