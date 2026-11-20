package com.swiftdrop.merchant.dto.response;

import java.time.Instant;
import java.util.UUID;

public class ApiKeyCreatedResponse {

    private UUID id;
    private String rawApiKey;
    private String keyPrefix;
    private String environment;
    private String label;
    private String scopes;
    private Instant expiresAt;

    public ApiKeyCreatedResponse() {
    }

    public ApiKeyCreatedResponse(UUID id, String rawApiKey, String keyPrefix, String environment, String label, String scopes, Instant expiresAt) {
        this.id = id;
        this.rawApiKey = rawApiKey;
        this.keyPrefix = keyPrefix;
        this.environment = environment;
        this.label = label;
        this.scopes = scopes;
        this.expiresAt = expiresAt;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getRawApiKey() {
        return rawApiKey;
    }

    public void setRawApiKey(String rawApiKey) {
        this.rawApiKey = rawApiKey;
    }

    public String getKeyPrefix() {
        return keyPrefix;
    }

    public void setKeyPrefix(String keyPrefix) {
        this.keyPrefix = keyPrefix;
    }

    public String getEnvironment() {
        return environment;
    }

    public void setEnvironment(String environment) {
        this.environment = environment;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getScopes() {
        return scopes;
    }

    public void setScopes(String scopes) {
        this.scopes = scopes;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }
}
