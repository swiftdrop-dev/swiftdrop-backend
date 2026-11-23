package com.swiftdrop.auth.dto.request;

import jakarta.validation.constraints.NotBlank;

public record UpdateUserRoleRequest(
        @NotBlank(message = "role is required") String role
) {}
