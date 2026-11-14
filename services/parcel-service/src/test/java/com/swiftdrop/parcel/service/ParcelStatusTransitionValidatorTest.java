package com.swiftdrop.parcel.service;

import com.swiftdrop.parcel.exception.ParcelStatusConflictException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ParcelStatusTransitionValidatorTest {
    private final ParcelStatusTransitionValidator validator = new ParcelStatusTransitionValidator();

    @Test
    void acceptsEveryApprovedTransition() {
        assertDoesNotThrow(() -> validator.validate("pending", "in_transit"));
        assertDoesNotThrow(() -> validator.validate("in_transit", "failed"));
        assertDoesNotThrow(() -> validator.validate("failed", "in_transit"));
    }

    @Test
    void rejectsEveryUnapprovedKnownTransition() {
        String[] statuses = {"pending", "in_transit", "failed", "delivered"};
        for (String current : statuses) {
            for (String next : statuses) {
                boolean approved = (current.equals("pending") && next.equals("in_transit"))
                        || (current.equals("in_transit") && next.equals("failed"))
                        || (current.equals("failed") && next.equals("in_transit"));
                if (!approved) {
                    assertThrows(ParcelStatusConflictException.class,
                            () -> validator.validate(current, next), current + " -> " + next);
                }
            }
        }
    }

    @Test
    void rejectsDeliveredAsTerminalAndRejectsUnknownStatuses() {
        assertThrows(ParcelStatusConflictException.class, () -> validator.validate("delivered", "pending"));
        assertThrows(ParcelStatusConflictException.class, () -> validator.validate("in_transit", "delivered"));
        assertThrows(ParcelStatusConflictException.class, () -> validator.validate("legacy_status", "pending"));
        assertThrows(ParcelStatusConflictException.class, () -> validator.validate("pending", "OUT_FOR_DELIVERY"));
        assertThrows(ParcelStatusConflictException.class, () -> validator.validate(null, "in_transit"));
    }
}
