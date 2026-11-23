package com.swiftdrop.tracking.controller;

import com.swiftdrop.tracking.dto.response.CustomerTrackingResponse;
import com.swiftdrop.tracking.dto.response.PublicTrackingResponse;
import com.swiftdrop.tracking.service.TrackingQueryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tracking")
public class TrackingController {

    private final TrackingQueryService trackingQueryService;

    public TrackingController(TrackingQueryService trackingQueryService) {
        this.trackingQueryService = trackingQueryService;
    }

    /**
     * Privacy-safe public tracking endpoint. Exposes only high-level status timeline,
     * without any sender/recipient PII, phone numbers, full addresses, or courier internal IDs.
     */
    @GetMapping("/public/{trackingNumber}")
    public ResponseEntity<PublicTrackingResponse> getPublicTracking(
            @PathVariable("trackingNumber") String trackingNumber) {
        return ResponseEntity.ok(trackingQueryService.getPublicTracking(trackingNumber));
    }

    /**
     * Authenticated customer tracking endpoint.
     */
    @GetMapping("/{trackingNumber}")
    public ResponseEntity<CustomerTrackingResponse> getCustomerTracking(
            @PathVariable("trackingNumber") String trackingNumber,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles) {
        org.springframework.security.core.Authentication auth =
                org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        String effectiveUserId = userId;
        String effectiveRoles = userRoles;
        if (auth != null && auth.getPrincipal() instanceof com.swiftdrop.tracking.security.UserPrincipal principal) {
            effectiveUserId = principal.getId() != null ? principal.getId().toString() : principal.getEmail();
            effectiveRoles = "ROLE_" + principal.getRole().toUpperCase(java.util.Locale.ROOT);
        }
        return ResponseEntity.ok(trackingQueryService.getCustomerTracking(trackingNumber, effectiveUserId, effectiveRoles));
    }
}
