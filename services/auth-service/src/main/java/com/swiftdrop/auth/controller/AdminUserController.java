package com.swiftdrop.auth.controller;

import com.swiftdrop.auth.dto.request.UpdateUserRoleRequest;
import com.swiftdrop.auth.dto.request.UpdateUserStatusRequest;
import com.swiftdrop.auth.dto.response.UserAdminResponse;
import com.swiftdrop.auth.dto.response.UserStatsResponse;
import com.swiftdrop.auth.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth/admin")
public class AdminUserController {

    private final AuthService authService;

    public AdminUserController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping("/users")
    public ResponseEntity<Page<UserAdminResponse>> getUsers(
            @RequestParam(value = "query", required = false) String query,
            @RequestParam(value = "role", required = false) String role,
            @RequestParam(value = "status", required = false) String status,
            @PageableDefault(size = 20) Pageable pageable) {

        Boolean active = null;
        if (status != null && !status.isBlank() && !status.equalsIgnoreCase("all")) {
            active = "active".equalsIgnoreCase(status) || "true".equalsIgnoreCase(status);
        }

        Page<UserAdminResponse> page = authService.getUsers(query, role, active, pageable);
        return ResponseEntity.ok(page);
    }

    @GetMapping("/users/{userId}")
    public ResponseEntity<UserAdminResponse> getUserById(@PathVariable String userId) {
        return ResponseEntity.ok(authService.getUserById(userId));
    }

    @RequestMapping(value = "/users/{userId}/status", method = {RequestMethod.PATCH, RequestMethod.PUT})
    public ResponseEntity<UserAdminResponse> updateUserStatus(
            @PathVariable String userId,
            @Valid @RequestBody UpdateUserStatusRequest request,
            @RequestHeader(value = "X-User-Id", required = false) String headerCallerId) {

        String callerId = resolveCallerId(headerCallerId);
        UserAdminResponse response = authService.updateUserStatus(userId, request.active(), callerId);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/users/{userId}/role")
    public ResponseEntity<UserAdminResponse> updateUserRole(
            @PathVariable String userId,
            @Valid @RequestBody UpdateUserRoleRequest request,
            @RequestHeader(value = "X-User-Id", required = false) String headerCallerId) {

        String callerId = resolveCallerId(headerCallerId);
        UserAdminResponse response = authService.updateUserRole(userId, request.role(), callerId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/stats")
    public ResponseEntity<UserStatsResponse> getUserStats() {
        return ResponseEntity.ok(authService.getUserStats());
    }

    private String resolveCallerId(String headerCallerId) {
        if (headerCallerId != null && !headerCallerId.isBlank()) {
            return headerCallerId;
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getName() != null) {
            return auth.getName();
        }
        return "";
    }
}
