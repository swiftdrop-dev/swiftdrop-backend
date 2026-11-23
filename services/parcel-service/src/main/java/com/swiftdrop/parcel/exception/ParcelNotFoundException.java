package com.swiftdrop.parcel.exception;

public class ParcelNotFoundException extends RuntimeException {
    public ParcelNotFoundException(String parcelId) {
        super("Parcel not found: " + parcelId);
    }
}
