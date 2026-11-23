package com.swiftdrop.merchant.dto.response;

import com.swiftdrop.merchant.entity.enums.WebhookDeliveryStatus;

import java.time.Instant;
import java.util.UUID;

public class WebhookDeliveryResponse {

    private UUID id;
    private UUID endpointId;
    private String endpointUrl;
    private UUID merchantId;
    private String eventId;
    private String eventType;
    private String trackingNumber;
    private String parcelId;
    private WebhookDeliveryStatus status;
    private int attemptCount;
    private int maxAttempts;
    private Instant nextRetryAt;
    private Integer lastHttpStatus;
    private String lastError;
    private Instant createdAt;
    private Instant deliveredAt;

    public WebhookDeliveryResponse() {
    }

    public WebhookDeliveryResponse(UUID id, UUID endpointId, String endpointUrl, UUID merchantId,
                                 String eventId, String eventType, String trackingNumber, String parcelId,
                                 WebhookDeliveryStatus status, int attemptCount, int maxAttempts,
                                 Instant nextRetryAt, Integer lastHttpStatus, String lastError,
                                 Instant createdAt, Instant deliveredAt) {
        this.id = id;
        this.endpointId = endpointId;
        this.endpointUrl = endpointUrl;
        this.merchantId = merchantId;
        this.eventId = eventId;
        this.eventType = eventType;
        this.trackingNumber = trackingNumber;
        this.parcelId = parcelId;
        this.status = status;
        this.attemptCount = attemptCount;
        this.maxAttempts = maxAttempts;
        this.nextRetryAt = nextRetryAt;
        this.lastHttpStatus = lastHttpStatus;
        this.lastError = lastError;
        this.createdAt = createdAt;
        this.deliveredAt = deliveredAt;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getEndpointId() {
        return endpointId;
    }

    public void setEndpointId(UUID endpointId) {
        this.endpointId = endpointId;
    }

    public String getEndpointUrl() {
        return endpointUrl;
    }

    public void setEndpointUrl(String endpointUrl) {
        this.endpointUrl = endpointUrl;
    }

    public UUID getMerchantId() {
        return merchantId;
    }

    public void setMerchantId(UUID merchantId) {
        this.merchantId = merchantId;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getTrackingNumber() {
        return trackingNumber;
    }

    public void setTrackingNumber(String trackingNumber) {
        this.trackingNumber = trackingNumber;
    }

    public String getParcelId() {
        return parcelId;
    }

    public void setParcelId(String parcelId) {
        this.parcelId = parcelId;
    }

    public WebhookDeliveryStatus getStatus() {
        return status;
    }

    public void setStatus(WebhookDeliveryStatus status) {
        this.status = status;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public void setAttemptCount(int attemptCount) {
        this.attemptCount = attemptCount;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public Instant getNextRetryAt() {
        return nextRetryAt;
    }

    public void setNextRetryAt(Instant nextRetryAt) {
        this.nextRetryAt = nextRetryAt;
    }

    public Integer getLastHttpStatus() {
        return lastHttpStatus;
    }

    public void setLastHttpStatus(Integer lastHttpStatus) {
        this.lastHttpStatus = lastHttpStatus;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getDeliveredAt() {
        return deliveredAt;
    }

    public void setDeliveredAt(Instant deliveredAt) {
        this.deliveredAt = deliveredAt;
    }
}
