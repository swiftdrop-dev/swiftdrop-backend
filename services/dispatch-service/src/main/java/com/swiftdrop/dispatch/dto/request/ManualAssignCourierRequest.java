package com.swiftdrop.dispatch.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public class ManualAssignCourierRequest {

    @NotNull(message = "Courier ID is required")
    private UUID courierId;

    @Size(max = 500)
    private String dispatcherNotes;

    public ManualAssignCourierRequest() {
    }

    public ManualAssignCourierRequest(UUID courierId, String dispatcherNotes) {
        this.courierId = courierId;
        this.dispatcherNotes = dispatcherNotes;
    }

    public UUID getCourierId() {
        return courierId;
    }

    public void setCourierId(UUID courierId) {
        this.courierId = courierId;
    }

    public String getDispatcherNotes() {
        return dispatcherNotes;
    }

    public void setDispatcherNotes(String dispatcherNotes) {
        this.dispatcherNotes = dispatcherNotes;
    }
}
