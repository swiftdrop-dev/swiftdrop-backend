package com.swiftdrop.dispatch.controller;

import com.swiftdrop.dispatch.dto.ApiResponse;
import com.swiftdrop.dispatch.dto.request.CreateDispatchZoneRequest;
import com.swiftdrop.dispatch.dto.response.DispatchZoneResponse;
import com.swiftdrop.dispatch.service.DispatchZoneService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/dispatch/zones")
public class DispatchZoneController {

    private final DispatchZoneService zoneService;

    public DispatchZoneController(DispatchZoneService zoneService) {
        this.zoneService = zoneService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<DispatchZoneResponse>> createZone(
            @Valid @RequestBody CreateDispatchZoneRequest request) {
        DispatchZoneResponse response = zoneService.createZone(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Dispatch zone created successfully", response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<DispatchZoneResponse>>> listActiveZones() {
        List<DispatchZoneResponse> zones = zoneService.listActiveZones();
        return ResponseEntity.ok(ApiResponse.success(zones));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<DispatchZoneResponse>> getZoneById(@PathVariable("id") UUID id) {
        DispatchZoneResponse response = zoneService.getZoneById(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
