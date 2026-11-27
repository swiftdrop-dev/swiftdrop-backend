package com.swiftdrop.dispatch.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public class ManualAssignCourierRequest {

    @NotNull(message = "Courier ID is required")
    @jakarta.validation.constraints.NotBlank(message = "Courier ID is required")
    @Size(max = 64)
    private String courierId;

    @Size(max = 500)
    private String dispatcherNotes;

    public ManualAssignCourierRequest() {
    }

    public ManualAssignCourierRequest(String courierId, String dispatcherNotes) {
        this.courierId = courierId;
        this.dispatcherNotes = dispatcherNotes;
    }

    public String getCourierId() {
        return courierId;
    }

    public void setCourierId(String courierId) {
        this.courierId = courierId;
    }

    public String getDispatcherNotes() {
        return dispatcherNotes;
    }

    public void setDispatcherNotes(String dispatcherNotes) {
        this.dispatcherNotes = dispatcherNotes;
    }
}
