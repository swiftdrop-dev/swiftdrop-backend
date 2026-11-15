package com.swiftdrop.gateway.service.impl;

import com.swiftdrop.gateway.dto.response.ChatbotMessageResponse;
import com.swiftdrop.gateway.dto.response.ChatbotUsageResponse;
import com.swiftdrop.gateway.exception.RateLimitExceededException;
import com.swiftdrop.gateway.service.ChatbotService;
import com.swiftdrop.gateway.service.GeminiService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ChatbotServiceImpl implements ChatbotService {

    private static final String PARCEL_LOOKUP_UNAVAILABLE =
            "Parcel-specific details are not available through chat. Please use the authenticated tracking or shipment view.";
    private static final java.util.regex.Pattern TRACKING_NUMBER =
            java.util.regex.Pattern.compile("(?i)\\b(?:SWF|SWD)-[0-9A-Z-]+\\b");

    @Value("${chatbot.free-limit:4}")
    private int freeLimit;

    private final GeminiService geminiService;
    // Process-local quota only. This is not a distributed rate limiter.
    private final Map<String, Integer> usageByQuotaKey = new ConcurrentHashMap<>();

    public ChatbotServiceImpl(GeminiService geminiService) {
        this.geminiService = geminiService;
    }

    @Override
    public ChatbotMessageResponse processMessage(String quotaKey, String userMessage) {
        String key = requireQuotaKey(quotaKey);
        int used = usageByQuotaKey.compute(key, (ignored, current) -> {
            int prior = current == null ? 0 : current;
            if (prior >= freeLimit) {
                throw new RateLimitExceededException(
                        "You have reached your " + freeLimit + "-message free limit. Please try again later.",
                        freeLimit);
            }
            return prior + 1;
        });
        int remaining = Math.max(0, freeLimit - used);

        if (isParcelSpecificRequest(userMessage)) {
            return new ChatbotMessageResponse(PARCEL_LOOKUP_UNAVAILABLE, remaining, freeLimit);
        }

        Optional<String> geminiReply = geminiService.generateResponse(userMessage);
        String reply = geminiReply.orElseGet(() -> generateDomainResponse(userMessage));
        return new ChatbotMessageResponse(reply, remaining, freeLimit);
    }

    @Override
    public ChatbotUsageResponse getUsage(String quotaKey) {
        String key = requireQuotaKey(quotaKey);
        int used = usageByQuotaKey.getOrDefault(key, 0);
        int remaining = Math.max(0, freeLimit - used);
        return new ChatbotUsageResponse(used, remaining, freeLimit, used >= freeLimit);
    }

    @Override
    public void resetUsage(String quotaKey) {
        if (quotaKey != null && !quotaKey.isBlank()) {
            usageByQuotaKey.remove(quotaKey);
        }
    }

    private static String requireQuotaKey(String quotaKey) {
        if (quotaKey == null || quotaKey.isBlank()) {
            throw new IllegalArgumentException("A server-derived chatbot quota key is required");
        }
        return quotaKey;
    }

    private static boolean isParcelSpecificRequest(String message) {
        if (message == null) return false;
        String normalized = message.toLowerCase(Locale.ROOT);
        return TRACKING_NUMBER.matcher(message).find()
                || normalized.contains("my parcel")
                || normalized.contains("my package")
                || normalized.contains("my shipment")
                || normalized.contains("my order")
                || normalized.contains("parcel status")
                || normalized.contains("shipment status")
                || normalized.contains("where is my");
    }

    private static String generateDomainResponse(String userMessage) {
        if (userMessage == null || userMessage.trim().isEmpty()) {
            return "How can I help you with SwiftDrop today?";
        }
        String msg = userMessage.toLowerCase(Locale.ROOT).trim();
        if (msg.matches(".*\\b(hi|hello|hey|good\\s*morning|good\\s*afternoon|good\\s*evening)\\b.*")) {
            return "Hello! I'm SwiftBot, your SwiftDrop assistant. I can help with general delivery information. How can I help?";
        }
        if (msg.contains("track") || msg.contains("where is") || msg.contains("status")) {
            return "For parcel-specific updates, please use the authenticated tracking or shipment view.";
        }
        if (msg.contains("book") || msg.contains("send parcel") || msg.contains("create parcel") || msg.contains("order")) {
            return "Customers can book a parcel from the dashboard. Merchants can use the merchant booking tools.";
        }
        if (msg.contains("price") || msg.contains("cost") || msg.contains("rate") || msg.contains("fee") || msg.contains("how much")) {
            return "Delivery rates depend on route, parcel size and service options. Use the booking page for an estimate.";
        }
        if (msg.contains("how long") || msg.contains("time") || msg.contains("fast") || msg.contains("duration") || msg.contains("same day")) {
            return "Delivery times depend on the route and service selected. The booking page shows the estimate for your shipment.";
        }
        if (msg.contains("courier") || msg.contains("driver") || msg.contains("rider")) {
            return "Courier assignment and delivery updates are available in the authenticated shipment view.";
        }
        if (msg.contains("contact") || msg.contains("support") || msg.contains("help") || msg.contains("call") || msg.contains("phone")) {
            return "Please use the support contact details listed in the SwiftDrop application.";
        }
        return "I'm SwiftBot. I can help with general information about bookings, delivery estimates and support.";
    }
}
