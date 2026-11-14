package com.swiftdrop.parcel.customer;

import java.util.Locale;

public enum ParcelStatus {
    CREATED, ASSIGNED, PICKED_UP, IN_TRANSIT, OUT_FOR_DELIVERY, DELIVERED, FAILED, CANCELLED, RETURNED;

    public String uiValue() {
        return this == CREATED ? "pending" : name().toLowerCase(Locale.ROOT);
    }

    public static ParcelStatus fromFilter(String value) {
        return "pending".equals(value) ? CREATED : valueOf(value.toUpperCase(Locale.ROOT));
    }
}
