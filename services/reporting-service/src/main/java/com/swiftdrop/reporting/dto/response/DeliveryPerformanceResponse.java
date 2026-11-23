package com.swiftdrop.reporting.dto.response;

public record DeliveryPerformanceResponse(
        Long totalDeliveries,
        Long delivered,
        Long failed,
        Double successRate,
        Double averageDeliveryTimeHours,
        Double onTimeRate
) {}
