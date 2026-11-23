package com.swiftdrop.courier.controller;

import com.swiftdrop.courier.dto.response.CourierResponse;
import com.swiftdrop.courier.service.CourierService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.swiftdrop.courier.dto.request.CourierAvailabilityRequest;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@RestController
@RequestMapping("/api/v1/couriers")
public class CourierController {
    private final CourierService courierService;

    public CourierController(CourierService courierService) {
        this.courierService = courierService;
    }

    private String resolveUserId(String headerUserId) {
        org.springframework.security.core.Authentication auth =
                org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof com.swiftdrop.courier.security.UserPrincipal principal) {
            return principal.getId() != null ? principal.getId().toString() : principal.getEmail();
        }
        if (headerUserId != null && !headerUserId.isBlank()) {
            return headerUserId;
        }
        return "cou_001";
    }

    @GetMapping("/me")
    public ResponseEntity<CourierResponse> getMyProfile(
            @RequestHeader(value = "X-User-Id", required = false) String headerUserId) {
        String userId = resolveUserId(headerUserId);
        return ResponseEntity.ok(courierService.getCourierByUserId(userId));
    }

    @PutMapping("/me/availability")
    public ResponseEntity<CourierResponse> updateMyAvailability(
            @RequestHeader(value = "X-User-Id", required = false) String headerUserId,
            @RequestBody CourierAvailabilityRequest request) {
        String userId = resolveUserId(headerUserId);
        return ResponseEntity.ok(courierService.updateAvailability(userId, request.isAvailable()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CourierResponse> getCourierById(@PathVariable String id) {
        return ResponseEntity.ok(courierService.getCourierById(id));
    }

    @GetMapping
    public ResponseEntity<java.util.List<CourierResponse>> getAllCouriers() {
        return ResponseEntity.ok(courierService.getAllCouriers());
    }

    @GetMapping("/summary")
    public ResponseEntity<java.util.Map<String, Long>> getSummary() {
        return ResponseEntity.ok(courierService.getCourierSummary());
    }
}

