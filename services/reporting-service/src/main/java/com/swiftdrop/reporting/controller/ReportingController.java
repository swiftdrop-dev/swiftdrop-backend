package com.swiftdrop.reporting.controller;

import com.swiftdrop.reporting.dto.response.*;
import com.swiftdrop.reporting.service.ReportingAggregationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/reports")
public class ReportingController {

    private final ReportingAggregationService aggregationService;

    public ReportingController(ReportingAggregationService aggregationService) {
        this.aggregationService = aggregationService;
    }

    private String resolveEffectiveRole(String headerRoles) {
        org.springframework.security.core.Authentication auth =
                org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof com.swiftdrop.reporting.security.UserPrincipal principal) {
            return "ROLE_" + principal.getRole().toUpperCase(java.util.Locale.ROOT);
        }
        if (headerRoles != null && !headerRoles.isBlank()) {
            return headerRoles;
        }
        return null;
    }

    private String verifyPrivilegedRole(String userRoles) {
        String role = resolveEffectiveRole(userRoles);
        if (role == null || role.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing authentication role");
        }
        String lower = role.toLowerCase(java.util.Locale.ROOT);
        if (!lower.contains("admin") && !lower.contains("dispatcher") && !lower.contains("staff")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied: Admin or Dispatcher role required");
        }
        return role;
    }

    private String verifyAdminRole(String userRoles) {
        String role = resolveEffectiveRole(userRoles);
        if (role == null || role.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing authentication role");
        }
        String lower = role.toLowerCase(java.util.Locale.ROOT);
        if (!lower.contains("admin")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied: Admin role required for merchant reporting");
        }
        return role;
    }

    @GetMapping("/dashboard-kpis")
    public ResponseEntity<DashboardKpisResponse> getDashboardKpis(
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        String role = verifyPrivilegedRole(userRoles);
        return ResponseEntity.ok(aggregationService.getDashboardKpis(role, authHeader));
    }

    @GetMapping("/parcel-volume")
    public ResponseEntity<ParcelVolumeReportResponse> getParcelVolume(
            @RequestParam(value = "days", defaultValue = "7") int days,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        String role = verifyPrivilegedRole(userRoles);
        return ResponseEntity.ok(aggregationService.getParcelVolume(days, role, authHeader));
    }

    @GetMapping("/status-distribution")
    public ResponseEntity<StatusDistributionResponse> getStatusDistribution(
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        String role = verifyPrivilegedRole(userRoles);
        return ResponseEntity.ok(aggregationService.getStatusDistribution(role, authHeader));
    }

    @GetMapping("/delivery-performance")
    public ResponseEntity<DeliveryPerformanceResponse> getDeliveryPerformance(
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        String role = verifyPrivilegedRole(userRoles);
        return ResponseEntity.ok(aggregationService.getDeliveryPerformance(role, authHeader));
    }

    @GetMapping("/courier-performance")
    public ResponseEntity<CourierPerformanceResponse> getCourierPerformance(
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        String role = verifyPrivilegedRole(userRoles);
        return ResponseEntity.ok(aggregationService.getCourierPerformance(role, authHeader));
    }

    @GetMapping("/merchant-volume")
    public ResponseEntity<MerchantVolumeResponse> getMerchantVolume(
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        String role = verifyAdminRole(userRoles);
        return ResponseEntity.ok(aggregationService.getMerchantVolume(role, authHeader));
    }
}

