package com.swiftdrop.merchant.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "merchant_webhook_delivery_attempts")
public class WebhookDeliveryAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "delivery_id", nullable = false)
    private WebhookDelivery delivery;

    @Column(name = "attempt_number", nullable = false)
    private int attemptNumber;

    @Column(name = "http_status")
    private Integer httpStatus;

    @Column(name = "response_body", columnDefinition = "TEXT")
    private String responseBody;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "attempted_at", nullable = false, updatable = false)
    private Instant attemptedAt;

    public WebhookDeliveryAttempt() {
    }

    public WebhookDeliveryAttempt(WebhookDelivery delivery, int attemptNumber, Integer httpStatus,
                                  String responseBody, String errorMessage, Long durationMs) {
        this.delivery = delivery;
        this.attemptNumber = attemptNumber;
        this.httpStatus = httpStatus;
        this.responseBody = responseBody;
        this.errorMessage = errorMessage;
        this.durationMs = durationMs;
    }

    @PrePersist
    protected void onCreate() {
        if (this.attemptedAt == null) {
            this.attemptedAt = Instant.now();
        }
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public WebhookDelivery getDelivery() {
        return delivery;
    }

    public void setDelivery(WebhookDelivery delivery) {
        this.delivery = delivery;
    }

    public int getAttemptNumber() {
        return attemptNumber;
    }

    public void setAttemptNumber(int attemptNumber) {
        this.attemptNumber = attemptNumber;
    }

    public Integer getHttpStatus() {
        return httpStatus;
    }

    public void setHttpStatus(Integer httpStatus) {
        this.httpStatus = httpStatus;
    }

    public String getResponseBody() {
        return responseBody;
    }

    public void setResponseBody(String responseBody) {
        this.responseBody = responseBody;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(Long durationMs) {
        this.durationMs = durationMs;
    }

    public Instant getAttemptedAt() {
        return attemptedAt;
    }

    public void setAttemptedAt(Instant attemptedAt) {
        this.attemptedAt = attemptedAt;
    }
}
