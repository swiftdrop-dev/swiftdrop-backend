package com.swiftdrop.merchant.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class WebhookEndpointCreatedResponse extends WebhookEndpointResponse {

    private String secret;

    public WebhookEndpointCreatedResponse() {
        super();
    }

    public WebhookEndpointCreatedResponse(UUID id, UUID merchantId, String url, String maskedSecret,
                                          String description, boolean isActive, List<String> subscribedEvents,
                                          Instant createdAt, Instant updatedAt, String secret) {
        super(id, merchantId, url, maskedSecret, description, isActive, subscribedEvents, createdAt, updatedAt);
        this.secret = secret;
    }

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }
}
