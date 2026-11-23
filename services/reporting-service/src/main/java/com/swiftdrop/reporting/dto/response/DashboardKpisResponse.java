package com.swiftdrop.reporting.dto.response;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;

public record DashboardKpisResponse(
        Long totalParcels,
        Long pendingParcels,
        Long unassignedDeliveries,
        Long assignedDeliveries,
        Long inTransitDeliveries,
        Long deliveredParcels,
        Long failedDeliveries,
        Long activeCouriers,
        Long totalCouriers,
        Long registeredCustomers,
        Long registeredMerchants,
        Long pendingMerchantApplications,
        Double totalRevenue,
        Double successRate,
        Double averageDeliveryTimeHours,
        boolean isPartial,
        Map<String, String> sourceAvailability,
        Instant dataFreshness
) {
    public DashboardKpisResponse(
            Long totalParcels,
            Long pendingParcels,
            Long unassignedDeliveries,
            Long assignedDeliveries,
            Long inTransitDeliveries,
            Long deliveredParcels,
            Long failedDeliveries,
            Long activeCouriers,
            Long totalCouriers,
            Long registeredCustomers,
            Long registeredMerchants,
            Long pendingMerchantApplications,
            Double totalRevenue,
            Double successRate,
            Double averageDeliveryTimeHours
    ) {
        this(totalParcels, pendingParcels, unassignedDeliveries, assignedDeliveries,
                inTransitDeliveries, deliveredParcels, failedDeliveries, activeCouriers,
                totalCouriers, registeredCustomers, registeredMerchants,
                pendingMerchantApplications, totalRevenue, successRate,
                averageDeliveryTimeHours, false, Collections.emptyMap(), Instant.now());
    }
}
