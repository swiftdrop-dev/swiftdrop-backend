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

    @GetMapping("/me")
    public ResponseEntity<CourierResponse> getMyProfile(
            @RequestHeader(value = "X-User-Id", required = false) String headerUserId) {
        String userId = headerUserId != null && !headerUserId.isBlank() ? headerUserId : "cou_001";
        return ResponseEntity.ok(courierService.getCourierByUserId(userId));
    }

    @PutMapping("/me/availability")
    public ResponseEntity<CourierResponse> updateMyAvailability(
            @RequestHeader(value = "X-User-Id", required = false) String headerUserId,
            @RequestBody CourierAvailabilityRequest request) {
        String userId = headerUserId != null && !headerUserId.isBlank() ? headerUserId : "cou_001";
        return ResponseEntity.ok(courierService.updateAvailability(userId, request.isAvailable()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CourierResponse> getCourierById(@PathVariable String id) {
        return ResponseEntity.ok(courierService.getCourierById(id));
    }
}

