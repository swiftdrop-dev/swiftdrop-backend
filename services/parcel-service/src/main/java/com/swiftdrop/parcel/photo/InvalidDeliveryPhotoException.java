package com.swiftdrop.parcel.photo;

public class InvalidDeliveryPhotoException extends RuntimeException {
    public InvalidDeliveryPhotoException(String message) {
        super(message);
    }

    public InvalidDeliveryPhotoException(String message, Throwable cause) {
        super(message, cause);
    }
}
