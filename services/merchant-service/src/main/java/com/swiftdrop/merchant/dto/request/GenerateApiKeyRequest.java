package com.swiftdrop.merchant.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class GenerateApiKeyRequest {

    @NotBlank(message = "Label is required (e.g. Production Key, Staging Server)")
    @Size(max = 100)
    private String label = "Default API Key";

    private String environment = "PRODUCTION";

    private String scopes = "parcels:create,parcels:read,tracking:read";

    private Integer expiresInDays;

    public GenerateApiKeyRequest() {
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getEnvironment() {
        return environment;
    }

    public void setEnvironment(String environment) {
        this.environment = environment;
    }

    public String getScopes() {
        return scopes;
    }

    public void setScopes(String scopes) {
        this.scopes = scopes;
    }

    public Integer getExpiresInDays() {
        return expiresInDays;
    }

    public void setExpiresInDays(Integer expiresInDays) {
        this.expiresInDays = expiresInDays;
    }
}
