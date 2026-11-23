package com.swiftdrop.reporting.dto.response;

public record CourierPerformanceItem(
        String courierId,
        String name,
        String vehicleType,
        String vehiclePlate,
        Double rating,
        boolean isAvailable,
        int deliveriesToday,
        int totalDeliveries
) {}
