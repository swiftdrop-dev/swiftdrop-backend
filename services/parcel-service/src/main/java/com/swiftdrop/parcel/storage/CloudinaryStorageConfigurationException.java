package com.swiftdrop.parcel.storage;

/** Configuration failure that never includes configured values. */
public class CloudinaryStorageConfigurationException extends IllegalStateException {
    public CloudinaryStorageConfigurationException() {
        super("Cloudinary storage is not configured; set CLOUDINARY_CLOUD_NAME, "
                + "CLOUDINARY_API_KEY, and CLOUDINARY_API_SECRET in the process environment");
    }
}
