package com.swiftdrop.gateway.controller;

import com.swiftdrop.gateway.dto.request.ChatbotMessageRequest;
import com.swiftdrop.gateway.dto.response.ChatbotMessageResponse;
import com.swiftdrop.gateway.dto.response.ChatbotUsageResponse;
import com.swiftdrop.gateway.security.AuthenticatedJwtPrincipal;
import com.swiftdrop.gateway.service.ChatbotService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/chatbot")
public class ChatbotController {

    private final ChatbotService chatbotService;

    public ChatbotController(ChatbotService chatbotService) {
        this.chatbotService = chatbotService;
    }

    @PostMapping("/message")
    public ResponseEntity<ChatbotMessageResponse> sendMessage(
            @Valid @RequestBody ChatbotMessageRequest request,
            Authentication authentication,
            HttpServletRequest servletRequest) {
        String quotaKey = authenticatedQuotaKey(authentication);
        if (quotaKey == null) {
            String remoteAddress = servletRequest.getRemoteAddr();
            quotaKey = "remote-ip:" + (remoteAddress == null || remoteAddress.isBlank() ? "unknown" : remoteAddress);
        }
        return ResponseEntity.ok(chatbotService.processMessage(quotaKey, request.getMessage()));
    }

    @GetMapping("/usage")
    public ResponseEntity<ChatbotUsageResponse> getUsage(Authentication authentication) {
        String quotaKey = authenticatedQuotaKey(authentication);
        if (quotaKey == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(chatbotService.getUsage(quotaKey));
    }

    @PostMapping("/reset")
    public ResponseEntity<Void> resetUsage() {
        // Closed until a trusted admin identity contract is available.
        return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    private String authenticatedQuotaKey(Authentication authentication) {
        if (authentication != null && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof AuthenticatedJwtPrincipal principal) {
            // The email subject is only an opaque, server-derived quota partition; it is not a domain UUID.
            return "jwt-subject:" + principal.subject();
        }
        return null;
    }
}
