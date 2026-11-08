package com.swiftdrop.gateway.service;

import com.swiftdrop.gateway.dto.response.ChatbotMessageResponse;
import com.swiftdrop.gateway.dto.response.ChatbotUsageResponse;

public interface ChatbotService {

    /**
     * Process an incoming chatbot message for a specific user,
     * enforcing the message rate limit.
     */
    ChatbotMessageResponse processMessage(String userId, String userMessage);

    /**
     * Retrieve current usage statistics for a user.
     */
    ChatbotUsageResponse getUsage(String userId);

    /**
     * Reset the usage counter for a user (useful for testing or upgrades).
     */
    void resetUsage(String userId);
}
