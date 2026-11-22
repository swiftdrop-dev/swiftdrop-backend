package com.swiftdrop.merchant.dto;

import java.util.regex.Pattern;

/** Validates an opaque Parcel Service identifier used in URL path segments. */
public final class ParcelId {
    private static final Pattern PATH_SEGMENT = Pattern.compile("[A-Za-z0-9_-]{1,64}");

    private ParcelId() {}

    public static boolean isValid(String id) {
        return id != null && PATH_SEGMENT.matcher(id).matches();
    }
}
