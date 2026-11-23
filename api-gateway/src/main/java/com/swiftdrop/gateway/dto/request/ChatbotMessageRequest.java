package com.swiftdrop.gateway.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ChatbotMessageRequest {

    @NotBlank(message = "Message must not be blank")
    @Size(max = 1000, message = "Message must not exceed 1000 characters")
    private String message;

    public ChatbotMessageRequest() {
    }

    public ChatbotMessageRequest(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

}
