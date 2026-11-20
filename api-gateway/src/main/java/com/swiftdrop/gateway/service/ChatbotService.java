package com.swiftdrop.gateway.service;

import com.swiftdrop.gateway.dto.response.ChatbotMessageResponse;
import com.swiftdrop.gateway.dto.response.ChatbotUsageResponse;

public interface ChatbotService {

    /**
     * Process a message under a quota key derived by the server, enforcing the local message limit.
     */
    ChatbotMessageResponse processMessage(String quotaKey, String userMessage);

    /**
     * Retrieve current usage statistics for a user.
     */
    ChatbotUsageResponse getUsage(String quotaKey);

    /**
     * Reset the usage counter for a user (useful for testing or upgrades).
     */
    void resetUsage(String quotaKey);
}
