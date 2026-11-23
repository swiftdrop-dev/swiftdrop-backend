package com.swiftdrop.auth.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/** Validates required external configuration without retaining or logging credentials. */
@Component
public class RequiredAuthEnvironment {

    public RequiredAuthEnvironment(@Value("${DB_URL}") String databaseUrl,
                                   @Value("${DB_USERNAME}") String databaseUsername,
                                   @Value("${DB_PASSWORD}") String databasePassword,
                                   @Value("${JWT_SECRET}") String jwtSecret) {
        requireValue("DB_URL", databaseUrl);
        requireValue("DB_USERNAME", databaseUsername);
        requireValue("DB_PASSWORD", databasePassword);
        requireValue("JWT_SECRET", jwtSecret);
        if (jwtSecret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("JWT_SECRET must contain at least 32 bytes of key material");
        }
    }

    private void requireValue(String propertyName, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(propertyName + " must be configured");
        }
    }
}
