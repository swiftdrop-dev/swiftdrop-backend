package com.swiftdrop.merchant.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class WebhookEndpointResponse {

    private UUID id;
    private UUID merchantId;
    private String url;
    private String maskedSecret;
    private String description;
    private boolean isActive;
    private List<String> subscribedEvents;
    private Instant createdAt;
    private Instant updatedAt;

    public WebhookEndpointResponse() {
    }

    public WebhookEndpointResponse(UUID id, UUID merchantId, String url, String maskedSecret,
                                 String description, boolean isActive, List<String> subscribedEvents,
                                 Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.merchantId = merchantId;
        this.url = url;
        this.maskedSecret = maskedSecret;
        this.description = description;
        this.isActive = isActive;
        this.subscribedEvents = subscribedEvents;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getMerchantId() {
        return merchantId;
    }

    public void setMerchantId(UUID merchantId) {
        this.merchantId = merchantId;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getMaskedSecret() {
        return maskedSecret;
    }

    public void setMaskedSecret(String maskedSecret) {
        this.maskedSecret = maskedSecret;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isActive() {
        return isActive;
    }

    public void setActive(boolean active) {
        isActive = active;
    }

    public List<String> getSubscribedEvents() {
        return subscribedEvents;
    }

    public void setSubscribedEvents(List<String> subscribedEvents) {
        this.subscribedEvents = subscribedEvents;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
