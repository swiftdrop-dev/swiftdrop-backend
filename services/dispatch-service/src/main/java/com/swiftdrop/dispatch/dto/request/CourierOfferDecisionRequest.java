package com.swiftdrop.dispatch.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class CourierOfferDecisionRequest {

    @NotNull(message = "Decision (accept=true, decline=false) is required")
    private Boolean accept;

    @Size(max = 255)
    private String reason;

    public CourierOfferDecisionRequest() {
    }

    public CourierOfferDecisionRequest(Boolean accept, String reason) {
        this.accept = accept;
        this.reason = reason;
    }

    public Boolean getAccept() {
        return accept;
    }

    public void setAccept(Boolean accept) {
        this.accept = accept;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
