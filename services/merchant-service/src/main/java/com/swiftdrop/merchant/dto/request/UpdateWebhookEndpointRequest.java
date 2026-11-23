package com.swiftdrop.merchant.dto.request;

import jakarta.validation.constraints.Size;

import java.util.List;

public class UpdateWebhookEndpointRequest {

    @Size(max = 1024, message = "Webhook URL must not exceed 1024 characters")
    private String url;

    @Size(max = 255, message = "Description must not exceed 255 characters")
    private String description;

    private List<String> subscribedEvents;

    private Boolean isActive;

    public UpdateWebhookEndpointRequest() {
    }

    public UpdateWebhookEndpointRequest(String url, String description, List<String> subscribedEvents, Boolean isActive) {
        this.url = url;
        this.description = description;
        this.subscribedEvents = subscribedEvents;
        this.isActive = isActive;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<String> getSubscribedEvents() {
        return subscribedEvents;
    }

    public void setSubscribedEvents(List<String> subscribedEvents) {
        this.subscribedEvents = subscribedEvents;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean active) {
        isActive = active;
    }
}
