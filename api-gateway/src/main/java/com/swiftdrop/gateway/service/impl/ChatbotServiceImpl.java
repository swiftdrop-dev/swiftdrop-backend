package com.swiftdrop.gateway.service.impl;

import com.swiftdrop.gateway.dto.response.ChatbotMessageResponse;
import com.swiftdrop.gateway.dto.response.ChatbotUsageResponse;
import com.swiftdrop.gateway.exception.RateLimitExceededException;
import com.swiftdrop.gateway.model.ParcelRecord;
import com.swiftdrop.gateway.service.ChatbotService;
import com.swiftdrop.gateway.service.GeminiService;
import com.swiftdrop.gateway.service.ParcelDatabaseService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class ChatbotServiceImpl implements ChatbotService {

    @Value("${chatbot.free-limit:4}")
    private int freeLimit;

    private final GeminiService geminiService;
    private final ParcelDatabaseService parcelDatabaseService;

    // In-memory thread-safe storage for tracking user message counts
    private final Map<String, AtomicInteger> userUsageMap = new ConcurrentHashMap<>();

    public ChatbotServiceImpl(GeminiService geminiService, ParcelDatabaseService parcelDatabaseService) {
        this.geminiService = geminiService;
        this.parcelDatabaseService = parcelDatabaseService;
    }

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

        // 1. Fetch live database context for this user and query
        String dbContext = parcelDatabaseService.buildDatabaseContext(effectiveUserId, userMessage);

        // 2. Query Gemini LLM with Database context
        Optional<String> geminiReply = geminiService.generateResponse(dbContext, userMessage);

        String reply;
        if (geminiReply.isPresent()) {
            reply = geminiReply.get();
        } else {
            // 3. Fallback to Domain Knowledge Engine (augmented with DB lookup)
            reply = generateDomainResponse(effectiveUserId, userMessage);
        }

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
     * Domain-specific assistant fallback with database lookup.
     */
    private String generateDomainResponse(String userId, String userMessage) {
        if (userMessage == null || userMessage.trim().isEmpty()) {
            return "How can I help you with your SwiftDrop delivery today?";
        }

        String msg = userMessage.toLowerCase(Locale.ROOT).trim();

        // Check if specific tracking number was typed
        Optional<String> trackingMention = parcelDatabaseService.extractTrackingNumber(userMessage);
        if (trackingMention.isPresent()) {
            Optional<ParcelRecord> found = parcelDatabaseService.findByTrackingNumber(trackingMention.get());
            if (found.isPresent()) {
                ParcelRecord p = found.get();
                return String.format(
                        "📦 Tracking Update for %s:\n"
                                + "• Status: %s\n"
                                + "• Package: %s\n"
                                + "• Recipient: %s (%s)\n"
                                + "• Courier: %s (Phone: %s)\n"
                                + "• Est. Delivery: %s",
                        p.getTrackingNumber(),
                        p.getStatus().toUpperCase(),
                        p.getDescription(),
                        p.getRecipientName(), p.getRecipientAddress(),
                        p.getCourierName(), p.getCourierPhone(),
                        p.getEstimatedDeliveryDate()
                );
            } else {
                return "I couldn't find any parcel with tracking number '" + trackingMention.get()
                        + "'. Please check the tracking code format (e.g. SWF-2024-001234).";
            }
        }

        // Check if user is asking about their own parcels
        if (msg.contains("my parcel") || msg.contains("my packages") || msg.contains("my orders") || msg.contains("my shipment")) {
            List<ParcelRecord> userParcels = parcelDatabaseService.findParcelsByUserId(userId);
            if (!userParcels.isEmpty()) {
                StringBuilder sb = new StringBuilder("Here are your active SwiftDrop shipments:\n");
                for (ParcelRecord p : userParcels) {
                    sb.append(String.format("• %s (%s) - Status: %s (Est: %s)\n",
                            p.getTrackingNumber(), p.getDescription(), p.getStatus().toUpperCase(), p.getEstimatedDeliveryDate()));
                }
                return sb.toString();
            }
        }

        // Greetings
        if (msg.matches(".*\\b(hi|hello|hey|good\\s*morning|good\\s*afternoon|good\\s*evening)\\b.*")) {
            return "Hello! 👋 I'm SwiftBot, your SwiftDrop assistant. I can assist you with tracking parcels, estimating delivery rates, booking shipments, and checking courier updates. How can I help you today?";
        }

        // Tracking general
        if (msg.contains("track") || msg.contains("where is") || msg.contains("status")) {
            return "🔍 To track a parcel:\n"
                    + "1. Go to 'Track Parcel' in the sidebar, or visit /track.\n"
                    + "2. Enter your Tracking Number (e.g. SWF-2024-001234) or type it right here in this chat!";
        }

        // Booking
        if (msg.contains("book") || msg.contains("send parcel") || msg.contains("create parcel") || msg.contains("order")) {
            return "📦 Booking a parcel:\n"
                    + "• Customers: Click 'Book Parcel' from your dashboard.\n"
                    + "• Merchants: Use 'Bulk Booking' to upload CSV files for multiple shipments at once!";
        }

        // Pricing
        if (msg.contains("price") || msg.contains("cost") || msg.contains("rate") || msg.contains("fee") || msg.contains("how much")) {
            return "💰 SwiftDrop delivery rates:\n"
                    + "• Base fee: Starting at LKR 350 for standard city delivery.\n"
                    + "• Rates adjust based on weight, distance, and fragile handling.\n"
                    + "You can get an instant estimate on the booking page!";
        }

        // Delivery time
        if (msg.contains("how long") || msg.contains("time") || msg.contains("fast") || msg.contains("duration") || msg.contains("same day")) {
            return "⚡ Delivery Timelines:\n"
                    + "• Same-Day Delivery: Colombo & Western province for bookings before 11:00 AM.\n"
                    + "• Next-Day Delivery: Island-wide across major cities.\n"
                    + "• Remote Areas: 2-3 business days.";
        }

        // Courier
        if (msg.contains("courier") || msg.contains("driver") || msg.contains("rider")) {
            return "🛵 Couriers:\n"
                    + "Our couriers receive live optimized routes. Proof of Delivery (photo or digital signature) is captured in real-time upon hand-off.";
        }

        // Support
        if (msg.contains("contact") || msg.contains("support") || msg.contains("help") || msg.contains("call") || msg.contains("phone")) {
            return "📞 SwiftDrop Support:\n"
                    + "• Hotline: +94 11 234 5678 (8:00 AM - 8:00 PM)\n"
                    + "• Email: support@swiftdrop.lk";
        }

        // Default Fallback
        return "I'm SwiftBot! I can help you with:\n"
                + "• 📦 Checking parcel status (Type a tracking number like SWF-2024-001234)\n"
                + "• 🚚 Viewing your current shipments\n"
                + "• 📝 Booking parcels & bulk merchant uploads\n"
                + "• 💵 Rates, fees and estimated delivery times\n\n"
                + "What would you like to know?";
    }
}
