package com.swiftdrop.parcel.storage;

/** Sanitized provider failure; provider details may contain sensitive request metadata. */
public class CloudinaryStorageException extends RuntimeException {
    public CloudinaryStorageException(String message) {
        super(message);
    }
}
