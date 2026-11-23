package com.swiftdrop.auth.dto.response;

public record UserStatsResponse(
        long totalUsers,
        long activeUsers,
        long deactivatedUsers,
        long customers,
        long merchants,
        long couriers,
        long dispatchers,
        long admins
) {}
