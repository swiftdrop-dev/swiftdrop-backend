package com.swiftdrop.dispatch.service;

import com.swiftdrop.dispatch.dto.response.CourierCandidateResponse;
import com.swiftdrop.dispatch.entity.CourierStatusCache;
import com.swiftdrop.dispatch.entity.enums.VehicleType;
import com.swiftdrop.dispatch.repository.CourierStatusCacheRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

@Service
public class ProximityMatchingService {

    private static final Logger log = LoggerFactory.getLogger(ProximityMatchingService.class);
    private static final double EARTH_RADIUS_KM = 6371.0;
    private static final double MAX_DISPATCH_RADIUS_KM = 30.0;
    private static final long LOCATION_FRESHNESS_MINUTES = 15;

    private final CourierStatusCacheRepository courierCacheRepository;

    public ProximityMatchingService(CourierStatusCacheRepository courierCacheRepository) {
        this.courierCacheRepository = courierCacheRepository;
    }

    @Transactional(readOnly = true)
    public List<CourierCandidateResponse> findBestCandidates(
            BigDecimal pickupLat,
            BigDecimal pickupLng,
            VehicleType requiredVehicle,
            BigDecimal parcelWeightKg,
            Set<String> excludedCourierIds) {
        return findBestCandidates(pickupLat, pickupLng, requiredVehicle, parcelWeightKg, excludedCourierIds, null);
    }

    @Transactional(readOnly = true)
    public List<CourierCandidateResponse> findBestCandidates(
            BigDecimal pickupLat,
            BigDecimal pickupLng,
            VehicleType requiredVehicle,
            BigDecimal parcelWeightKg,
            Set<String> excludedCourierIds,
            BigDecimal maxRadiusKm) {
        double radiusKm = resolveRadiusKm(maxRadiusKm);
        if (pickupLat == null || pickupLng == null) {
            return List.of();
        }

        List<CourierStatusCache> onlineCouriers = courierCacheRepository.findByIsOnlineTrueAndIsAvailableTrue();
        List<CourierCandidateResponse> candidates = new ArrayList<>();

        for (CourierStatusCache courier : onlineCouriers) {
            // Check exclusion list (e.g. couriers who previously declined this job)
            if (excludedCourierIds != null && excludedCourierIds.contains(courier.getCourierId())) {
                continue;
            }

            // Check capacity
            if (courier.getActiveDeliveriesCount() >= courier.getMaxCapacity()) {
                continue;
            }

            // Check vehicle compatibility
            if (!isVehicleCompatible(courier.getVehicleType(), requiredVehicle)) {
                continue;
            }

            // Check GPS coordinates present
            if (courier.getCurrentLatitude() == null || courier.getCurrentLongitude() == null || isStaleLocation(courier)) {
                continue;
            }

            double distanceKm = calculateHaversineDistanceKm(
                    pickupLat.doubleValue(), pickupLng.doubleValue(),
                    courier.getCurrentLatitude().doubleValue(), courier.getCurrentLongitude().doubleValue()
            );

            // Distance filter
            if (distanceKm > radiusKm) {
                continue;
            }

            // Score calculation (lower score = higher priority)
            // Weight 60% distance, 40% active load burden
            double matchScore = (distanceKm * 0.6) + (courier.getActiveDeliveriesCount() * 2.0);

            BigDecimal roundedDistance = BigDecimal.valueOf(distanceKm).setScale(2, RoundingMode.HALF_UP);
            candidates.add(new CourierCandidateResponse(
                    courier.getCourierId(),
                    courier.getCourierName(),
                    courier.getCourierPhone(),
                    courier.getVehicleType(),
                    roundedDistance,
                    courier.getActiveDeliveriesCount(),
                    courier.getMaxCapacity(),
                    matchScore
            ));
        }

        // Sort candidates by matchScore ascending
        candidates.sort(Comparator.comparingDouble(CourierCandidateResponse::getMatchScore));

        log.info("Found {} eligible courier candidates for pickup [{}, {}]", candidates.size(), pickupLat, pickupLng);
        return candidates;
    }

    private double resolveRadiusKm(BigDecimal maxRadiusKm) {
        if (maxRadiusKm == null) {
            return MAX_DISPATCH_RADIUS_KM;
        }
        if (maxRadiusKm.compareTo(BigDecimal.ZERO) <= 0 || maxRadiusKm.doubleValue() > MAX_DISPATCH_RADIUS_KM) {
            throw new IllegalArgumentException("maxRadiusKm must be greater than 0 and at most " + (int) MAX_DISPATCH_RADIUS_KM);
        }
        return maxRadiusKm.doubleValue();
    }

    private boolean isStaleLocation(CourierStatusCache courier) {
        Instant updatedAt = courier.getLastLocationUpdatedAt();
        return updatedAt != null && updatedAt.isBefore(Instant.now().minus(LOCATION_FRESHNESS_MINUTES, ChronoUnit.MINUTES));
    }

    public double calculateHaversineDistanceKm(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);

        double originLat = Math.toRadians(lat1);
        double targetLat = Math.toRadians(lat2);

        double a = Math.sin(dLat / 2.0) * Math.sin(dLat / 2.0) +
                Math.cos(originLat) * Math.cos(targetLat) *
                        Math.sin(dLon / 2.0) * Math.sin(dLon / 2.0);

        double c = 2.0 * Math.atan2(Math.sqrt(a), Math.sqrt(1.0 - a));
        return EARTH_RADIUS_KM * c;
    }

    public boolean isVehicleCompatible(VehicleType courierVehicle, VehicleType requiredVehicle) {
        if (requiredVehicle == null || courierVehicle == null) {
            return true;
        }

        int courierRank = getVehicleRank(courierVehicle);
        int requiredRank = getVehicleRank(requiredVehicle);

        // A vehicle of higher or equal capacity rank can deliver smaller parcel vehicle requirements
        return courierRank >= requiredRank;
    }

    private int getVehicleRank(VehicleType vehicleType) {
        return switch (vehicleType) {
            case BICYCLE -> 1;
            case MOTORCYCLE -> 2;
            case CAR -> 3;
            case VAN -> 4;
            case TRUCK -> 5;
        };
    }
}
