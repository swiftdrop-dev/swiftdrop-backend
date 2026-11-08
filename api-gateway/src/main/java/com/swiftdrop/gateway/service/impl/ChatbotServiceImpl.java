package com.swiftdrop.gateway.service.impl;

import com.swiftdrop.gateway.dto.response.ChatbotMessageResponse;
import com.swiftdrop.gateway.dto.response.ChatbotUsageResponse;
import com.swiftdrop.gateway.exception.RateLimitExceededException;
import com.swiftdrop.gateway.service.ChatbotService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class ChatbotServiceImpl implements ChatbotService {

    @Value("${chatbot.free-limit:4}")
    private int freeLimit;

    // In-memory thread-safe storage for tracking user message counts
    private final Map<String, AtomicInteger> userUsageMap = new ConcurrentHashMap<>();

    @Override
    public ChatbotMessageResponse processMessage(String userId, String userMessage) {
        String effectiveUserId = (userId != null && !userId.isBlank()) ? userId.trim() : "anonymous";

        // Check and update usage count atomically
        AtomicInteger counter = userUsageMap.computeIfAbsent(effectiveUserId, k -> new AtomicInteger(0));

        int currentCount = counter.get();
        if (currentCount >= freeLimit) {
            throw new RateLimitExceededException(
                    "You have reached your " + freeLimit + "-message free limit. Please upgrade your plan to continue chatting with SwiftBot.",
                    freeLimit
            );
        }

        // Increment count
        int newCount = counter.incrementAndGet();
        int remaining = Math.max(0, freeLimit - newCount);

        // Generate response
        String reply = generateDomainResponse(userMessage);

        return new ChatbotMessageResponse(reply, remaining, freeLimit);
    }

    @Override
    public ChatbotUsageResponse getUsage(String userId) {
        String effectiveUserId = (userId != null && !userId.isBlank()) ? userId.trim() : "anonymous";
        int used = userUsageMap.getOrDefault(effectiveUserId, new AtomicInteger(0)).get();
        int remaining = Math.max(0, freeLimit - used);
        boolean limitReached = used >= freeLimit;

        return new ChatbotUsageResponse(effectiveUserId, used, remaining, freeLimit, limitReached);
    }

    @Override
    public void resetUsage(String userId) {
        if (userId != null) {
            userUsageMap.remove(userId);
        }
    }

    /**
     * Domain-specific assistant intelligence for SwiftDrop Courier Platform.
     */
    private String generateDomainResponse(String userMessage) {
        if (userMessage == null || userMessage.trim().isEmpty()) {
            return "How can I help you with your SwiftDrop delivery today?";
        }

        String msg = userMessage.toLowerCase(Locale.ROOT).trim();

        // 1. Greetings
        if (msg.matches(".*\\b(hi|hello|hey|good\\s*morning|good\\s*afternoon|good\\s*evening)\\b.*")) {
            return "Hello! 👋 I'm SwiftBot, your SwiftDrop assistant. I can assist you with tracking parcels, estimating delivery rates, booking shipments, and understanding delivery steps. What can I do for you today?";
        }

        // 2. Tracking Parcels
        if (msg.contains("track") || msg.contains("where is my") || msg.contains("status")) {
            return "🔍 To track a parcel:\n"
                    + "1. Go to the 'Track Parcel' section in the sidebar, or visit /track.\n"
                    + "2. Enter your Tracking Number (e.g., SWD-98241).\n"
                    + "3. You will see live timeline updates from 'Booking Confirmed' to 'Out for Delivery' and 'Delivered'!";
        }

        // 3. Booking / Send Parcel
        if (msg.contains("book") || msg.contains("send parcel") || msg.contains("create parcel") || msg.contains("order")) {
            return "📦 Booking a parcel is easy:\n"
                    + "• Customers: Head to 'Book Parcel' from your dashboard, fill in sender/recipient details, parcel weight, and size.\n"
                    + "• Merchants: You can book single parcels or use 'Bulk Booking' to upload CSV files for multiple orders at once!";
        }

        // 4. Pricing / Cost / Rates / Calculation
        if (msg.contains("price") || msg.contains("cost") || msg.contains("rate") || msg.contains("fee") || msg.contains("how much")) {
            return "💰 SwiftDrop delivery charges are calculated based on:\n"
                    + "• Base fee: Starting at LKR 350 for standard city delivery.\n"
                    + "• Weight & Size: Small (up to 1kg), Medium (up to 5kg), and Large.\n"
                    + "• Distance & Fragile handling: Extra protection available for fragile goods.\n"
                    + "You can get an instant estimate on the booking page before confirming!";
        }

        // 5. Delivery Time / Speed
        if (msg.contains("how long") || msg.contains("time") || msg.contains("fast") || msg.contains("duration") || msg.contains("same day")) {
            return "⚡ Delivery Timelines:\n"
                    + "• Same-Day Delivery: Available within Colombo & Western province for bookings made before 11:00 AM.\n"
                    + "• Next-Day Delivery: Island-wide major cities.\n"
                    + "• Remote Areas: 2-3 business days.";
        }

        // 6. Courier / Driver
        if (msg.contains("courier") || msg.contains("driver") || msg.contains("rider")) {
            return "🛵 Our Couriers:\n"
                    + "Couriers receive delivery assignments with recipient contact numbers and navigation routes. Once delivered, proof of delivery (photo or digital signature) is captured instantly.";
        }

        // 7. Merchant / Bulk
        if (msg.contains("merchant") || msg.contains("bulk") || msg.contains("business") || msg.contains("store")) {
            return "💼 Merchant Accounts:\n"
                    + "Merchants enjoy special bulk delivery rates, webhook integrations, cash-on-delivery (COD) reconciliation, and an analytics dashboard to monitor daily fulfillment rates!";
        }

        // 8. Cash on Delivery (COD) / Payment
        if (msg.contains("cod") || msg.contains("cash") || msg.contains("pay") || msg.contains("payment")) {
            return "💳 Payment Options:\n"
                    + "We support Cash on Delivery (COD) and Online Card Payments. COD settlements for merchants are processed on a weekly schedule directly to your bank account.";
        }

        // 9. Contact / Support / Human Agent
        if (msg.contains("contact") || msg.contains("support") || msg.contains("help") || msg.contains("call") || msg.contains("email") || msg.contains("phone")) {
            return "📞 SwiftDrop Support:\n"
                    + "• Hotline: +94 11 234 5678 (8:00 AM - 8:00 PM daily)\n"
                    + "• Email: support@swiftdrop.lk\n"
                    + "• Live chat with a human agent is available during working hours!";
        }

        // 10. Thank you
        if (msg.matches(".*\\b(thanks|thank you|ok|okay|great|awesome|good)\\b.*")) {
            return "You're very welcome! 😊 Let me know if you need anything else regarding your SwiftDrop shipments.";
        }

        // Default Fallback
        return "I'm SwiftBot! I can help you with:\n"
                + "• 📦 Tracking your parcel status\n"
                + "• 📝 Booking parcels & bulk shipping\n"
                + "• 💵 Delivery pricing and fee estimates\n"
                + "• 🚚 Delivery timelines & courier updates\n\n"
                + "Could you please rephrase or specify which topic you'd like help with?";
    }
}
