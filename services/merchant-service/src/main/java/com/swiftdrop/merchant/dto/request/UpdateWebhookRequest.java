package com.swiftdrop.merchant.dto.request;

import jakarta.validation.constraints.Size;

public class UpdateWebhookRequest {

    @Size(max = 1024)
    private String webhookUrl;

    @Size(max = 255)
    private String webhookSecret;

    public UpdateWebhookRequest() {
    }

    public String getWebhookUrl() {
        return webhookUrl;
    }

    public void setWebhookUrl(String webhookUrl) {
        this.webhookUrl = webhookUrl;
    }

    public String getWebhookSecret() {
        return webhookSecret;
    }

    public void setWebhookSecret(String webhookSecret) {
        this.webhookSecret = webhookSecret;
    }
}
