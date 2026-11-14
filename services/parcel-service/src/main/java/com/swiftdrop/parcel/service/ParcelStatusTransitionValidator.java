package com.swiftdrop.parcel.service;

import com.swiftdrop.parcel.exception.ParcelStatusConflictException;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

@Component
public class ParcelStatusTransitionValidator {
    public static final String PENDING = "pending";
    public static final String IN_TRANSIT = "in_transit";
    public static final String FAILED = "failed";
    public static final String DELIVERED = "delivered";

    private static final Set<String> KNOWN_STATUSES = Set.of(PENDING, IN_TRANSIT, FAILED, DELIVERED);
    private static final Map<String, Set<String>> ALLOWED_TRANSITIONS = Map.of(
            PENDING, Set.of(IN_TRANSIT),
            IN_TRANSIT, Set.of(FAILED),
            FAILED, Set.of(IN_TRANSIT),
            DELIVERED, Set.of()
    );

    public void validate(String currentStatus, String requestedStatus) {
        if (currentStatus == null || !KNOWN_STATUSES.contains(currentStatus)) {
            throw new ParcelStatusConflictException("Parcel has an unknown current status");
        }
        if (requestedStatus == null || !KNOWN_STATUSES.contains(requestedStatus)) {
            throw new ParcelStatusConflictException("Requested parcel status is not supported");
        }
        if (!ALLOWED_TRANSITIONS.get(currentStatus).contains(requestedStatus)) {
            throw new ParcelStatusConflictException(
                    "Parcel status transition is not allowed: " + currentStatus + " -> " + requestedStatus);
        }
    }
}
