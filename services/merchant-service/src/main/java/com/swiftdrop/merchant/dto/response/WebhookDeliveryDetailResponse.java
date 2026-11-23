package com.swiftdrop.merchant.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class WebhookDeliveryDetailResponse extends WebhookDeliveryResponse {

    private String payload;
    private List<WebhookDeliveryAttemptResponse> attempts;

    public WebhookDeliveryDetailResponse() {
        super();
    }

    public WebhookDeliveryDetailResponse(WebhookDeliveryResponse base, String payload,
                                         List<WebhookDeliveryAttemptResponse> attempts) {
        super(base.getId(), base.getEndpointId(), base.getEndpointUrl(), base.getMerchantId(),
                base.getEventId(), base.getEventType(), base.getTrackingNumber(), base.getParcelId(),
                base.getStatus(), base.getAttemptCount(), base.getMaxAttempts(),
                base.getNextRetryAt(), base.getLastHttpStatus(), base.getLastError(),
                base.getCreatedAt(), base.getDeliveredAt());
        this.payload = payload;
        this.attempts = attempts;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public List<WebhookDeliveryAttemptResponse> getAttempts() {
        return attempts;
    }

    public void setAttempts(List<WebhookDeliveryAttemptResponse> attempts) {
        this.attempts = attempts;
    }
}
