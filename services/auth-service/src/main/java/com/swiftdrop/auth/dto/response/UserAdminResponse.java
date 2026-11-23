package com.swiftdrop.auth.dto.response;

import java.time.LocalDateTime;

public record UserAdminResponse(
        String id,
        String name,
        String email,
        String phone,
        String role,
        boolean active,
        String status,
        LocalDateTime createdAt
) {}
