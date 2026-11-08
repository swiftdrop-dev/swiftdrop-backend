package com.swiftdrop.gateway.controller;

import com.swiftdrop.gateway.dto.request.ChatbotMessageRequest;
import com.swiftdrop.gateway.dto.response.ChatbotMessageResponse;
import com.swiftdrop.gateway.dto.response.ChatbotUsageResponse;
import com.swiftdrop.gateway.service.ChatbotService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/chatbot")
public class ChatbotController {

    private final ChatbotService chatbotService;

    public ChatbotController(ChatbotService chatbotService) {
        this.chatbotService = chatbotService;
    }

    /**
     * Send a message to the chatbot.
     * Enforces the 4-free-message limit per user.
     */
    @PostMapping("/message")
    public ResponseEntity<ChatbotMessageResponse> sendMessage(
            @Valid @RequestBody ChatbotMessageRequest request,
            @RequestHeader(value = "X-User-Id", required = false) String headerUserId,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            HttpServletRequest servletRequest
    ) {
        String userId = resolveUserId(request.getUserId(), headerUserId, authHeader, servletRequest);
        ChatbotMessageResponse response = chatbotService.processMessage(userId, request.getMessage());
        return ResponseEntity.ok(response);
    }

    /**
     * Retrieve the remaining usage count for the current user.
     */
    @GetMapping("/usage")
    public ResponseEntity<ChatbotUsageResponse> getUsage(
            @RequestParam(value = "userId", required = false) String paramUserId,
            @RequestHeader(value = "X-User-Id", required = false) String headerUserId,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            HttpServletRequest servletRequest
    ) {
        String userId = resolveUserId(paramUserId, headerUserId, authHeader, servletRequest);
        ChatbotUsageResponse response = chatbotService.getUsage(userId);
        return ResponseEntity.ok(response);
    }

    /**
     * Reset the usage counter for testing or plan upgrade.
     */
    @PostMapping("/reset")
    public ResponseEntity<Void> resetUsage(
            @RequestParam(value = "userId", required = false) String paramUserId,
            @RequestHeader(value = "X-User-Id", required = false) String headerUserId,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            HttpServletRequest servletRequest
    ) {
        String userId = resolveUserId(paramUserId, headerUserId, authHeader, servletRequest);
        chatbotService.resetUsage(userId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Helper to resolve the user identifier from multiple potential sources.
     */
    private String resolveUserId(String bodyOrParamUserId, String headerUserId, String authHeader, HttpServletRequest request) {
        if (headerUserId != null && !headerUserId.isBlank()) {
            return headerUserId.trim();
        }
        if (bodyOrParamUserId != null && !bodyOrParamUserId.isBlank()) {
            return bodyOrParamUserId.trim();
        }
        if (authHeader != null && authHeader.startsWith("Bearer ") && authHeader.length() > 7) {
            String token = authHeader.substring(7).trim();
            if (!token.isBlank()) {
                return "token:" + token;
            }
        }
        String remoteAddr = request.getRemoteAddr();
        return (remoteAddr != null && !remoteAddr.isBlank()) ? "ip:" + remoteAddr : "anonymous";
    }
}
