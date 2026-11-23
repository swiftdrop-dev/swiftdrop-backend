package com.swiftdrop.pricing.controller;

import com.swiftdrop.pricing.dto.response.ZoneDto;
import com.swiftdrop.pricing.dto.response.ZoneRateDto;
import com.swiftdrop.pricing.service.ZoneService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/zones")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@Tag(name = "Zones & Rate Management", description = "Endpoints for administering zones and delivery rate matrices")
public class ZoneAdminController {

    private final ZoneService zoneService;

    @GetMapping
    @Operation(summary = "Get all active zones", description = "Public endpoint for drop-down lists in booking form and dispatcher UI")
    public ResponseEntity<List<ZoneDto>> getActiveZones() {
        return ResponseEntity.ok(zoneService.getAllActiveZones());
    }

    private String resolveEffectiveRole(String headerRoles) {
        org.springframework.security.core.Authentication auth =
                org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof com.swiftdrop.pricing.security.UserPrincipal principal) {
            return "ROLE_" + principal.getRole().toUpperCase(java.util.Locale.ROOT);
        }
        if (headerRoles != null && !headerRoles.isBlank()) {
            return headerRoles;
        }
        return null;
    }

    private void verifyAdminRole(String userRoles) {
        String role = resolveEffectiveRole(userRoles);
        if (role == null || role.isBlank()) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "Missing authentication role");
        }
        String lower = role.toLowerCase(java.util.Locale.ROOT);
        if (!lower.contains("admin")) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Access denied: Admin role required");
        }
    }

    @GetMapping("/admin/all")
    @Operation(summary = "Get all zones (including inactive)", description = "Admin view for zone configuration")
    public ResponseEntity<List<ZoneDto>> getAllZones(
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles) {
        verifyAdminRole(userRoles);
        return ResponseEntity.ok(zoneService.getAllZones());
    }

    @PostMapping("/admin")
    @Operation(summary = "Create a new delivery zone", description = "Admin only")
    public ResponseEntity<ZoneDto> createZone(
            @Valid @RequestBody ZoneDto dto,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles) {
        verifyAdminRole(userRoles);
        return ResponseEntity.status(HttpStatus.CREATED).body(zoneService.createZone(dto));
    }

    @PutMapping("/admin/{id}")
    @Operation(summary = "Update an existing delivery zone", description = "Admin only")
    public ResponseEntity<ZoneDto> updateZone(
            @PathVariable Long id,
            @Valid @RequestBody ZoneDto dto,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles) {
        verifyAdminRole(userRoles);
        return ResponseEntity.ok(zoneService.updateZone(id, dto));
    }

    @GetMapping("/admin/rates")
    @Operation(summary = "Get all configured cross-zone rates", description = "Admin rate matrix overview")
    public ResponseEntity<List<ZoneRateDto>> getAllRates(
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles) {
        verifyAdminRole(userRoles);
        return ResponseEntity.ok(zoneService.getAllRates());
    }

    @PutMapping("/admin/rates")
    @Operation(summary = "Create or update a zone-to-zone rate configuration", description = "Admin pricing table editor")
    public ResponseEntity<ZoneRateDto> saveOrUpdateRate(
            @Valid @RequestBody ZoneRateDto dto,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles) {
        verifyAdminRole(userRoles);
        return ResponseEntity.ok(zoneService.saveOrUpdateRate(dto));
    }
}
