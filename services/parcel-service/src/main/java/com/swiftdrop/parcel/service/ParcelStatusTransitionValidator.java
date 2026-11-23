package com.swiftdrop.parcel.service;

import com.swiftdrop.parcel.exception.ParcelStatusConflictException;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

@Component
public class ParcelStatusTransitionValidator {
    public static final String CREATED = "created";
    public static final String PENDING = "pending";
    public static final String ASSIGNED = "assigned";
    public static final String PICKED_UP = "picked_up";
    public static final String IN_TRANSIT = "in_transit";
    public static final String OUT_FOR_DELIVERY = "out_for_delivery";
    public static final String DELIVERED = "delivered";
    public static final String FAILED = "failed";
    public static final String DELIVERY_FAILED = "delivery_failed";
    public static final String CANCELLED = "cancelled";
    public static final String RETURNED = "returned";

    private static final Set<String> KNOWN_STATUSES = Set.of(
            CREATED, PENDING, ASSIGNED, PICKED_UP, IN_TRANSIT,
            OUT_FOR_DELIVERY, DELIVERED, FAILED, DELIVERY_FAILED, CANCELLED, RETURNED
    );

    private static final Map<String, Set<String>> ALLOWED_TRANSITIONS = Map.ofEntries(
            Map.entry(CREATED, Set.of(ASSIGNED, PICKED_UP, IN_TRANSIT, CANCELLED)),
            Map.entry(PENDING, Set.of(ASSIGNED, PICKED_UP, IN_TRANSIT, CANCELLED)),
            Map.entry(ASSIGNED, Set.of(PICKED_UP, IN_TRANSIT, CANCELLED)),
            Map.entry(PICKED_UP, Set.of(IN_TRANSIT, OUT_FOR_DELIVERY, FAILED, DELIVERY_FAILED, CANCELLED)),
            Map.entry(IN_TRANSIT, Set.of(OUT_FOR_DELIVERY, FAILED, DELIVERY_FAILED, RETURNED, CANCELLED)),
            Map.entry(OUT_FOR_DELIVERY, Set.of(IN_TRANSIT, FAILED, DELIVERY_FAILED, RETURNED, CANCELLED)),
            Map.entry(FAILED, Set.of(IN_TRANSIT, OUT_FOR_DELIVERY, RETURNED, CANCELLED)),
            Map.entry(DELIVERY_FAILED, Set.of(IN_TRANSIT, OUT_FOR_DELIVERY, RETURNED, CANCELLED)),
            Map.entry(DELIVERED, Set.of()),
            Map.entry(CANCELLED, Set.of()),
            Map.entry(RETURNED, Set.of())
    );

    public void validate(String currentStatus, String requestedStatus) {
        if (currentStatus == null) {
            throw new ParcelStatusConflictException("Parcel has an unknown current status");
        }
        if (requestedStatus == null) {
            throw new ParcelStatusConflictException("Requested parcel status is not supported");
        }
        String current = currentStatus.toLowerCase();
        String requested = requestedStatus.toLowerCase();
        if (!KNOWN_STATUSES.contains(current)) {
            throw new ParcelStatusConflictException("Parcel has an unknown current status");
        }
        if (!KNOWN_STATUSES.contains(requested)) {
            throw new ParcelStatusConflictException("Requested parcel status is not supported");
        }
        Set<String> allowed = ALLOWED_TRANSITIONS.get(current);
        if (allowed == null || !allowed.contains(requested)) {
            throw new ParcelStatusConflictException(
                    "Parcel status transition is not allowed: " + currentStatus + " -> " + requestedStatus);
        }
    }

    /** Validates the dedicated proof-gated completion path without opening ordinary status updates. */
    public void validateDeliveryCompletion(String currentStatus) {
        if (currentStatus == null) {
            throw new ParcelStatusConflictException("Parcel has an unknown current status");
        }
        String normalized = currentStatus.toLowerCase();
        if (!KNOWN_STATUSES.contains(normalized)) {
            throw new ParcelStatusConflictException("Parcel has an unknown current status");
        }
        if (!IN_TRANSIT.equals(normalized) && !OUT_FOR_DELIVERY.equals(normalized)) {
            throw new ParcelStatusConflictException("Only an in_transit or out_for_delivery parcel can be completed as delivered");
        }
    }
}
