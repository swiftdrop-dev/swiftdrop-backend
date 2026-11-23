package com.swiftdrop.merchant.dto.response;

import java.time.Instant;
import java.util.UUID;

public class WebhookDeliveryAttemptResponse {

    private UUID id;
    private int attemptNumber;
    private Integer httpStatus;
    private String responseBody;
    private String errorMessage;
    private Long durationMs;
    private Instant attemptedAt;

    public WebhookDeliveryAttemptResponse() {
    }

    public WebhookDeliveryAttemptResponse(UUID id, int attemptNumber, Integer httpStatus,
                                        String responseBody, String errorMessage, Long durationMs,
                                        Instant attemptedAt) {
        this.id = id;
        this.attemptNumber = attemptNumber;
        this.httpStatus = httpStatus;
        this.responseBody = responseBody;
        this.errorMessage = errorMessage;
        this.durationMs = durationMs;
        this.attemptedAt = attemptedAt;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
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
