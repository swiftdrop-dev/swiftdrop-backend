package com.swiftdrop.reporting.dto.response;

import java.util.List;

public record CourierPerformanceResponse(
        long totalCouriers,
        long activeCouriers,
        List<CourierPerformanceItem> couriers
) {}
