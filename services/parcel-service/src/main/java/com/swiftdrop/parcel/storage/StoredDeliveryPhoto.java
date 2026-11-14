package com.swiftdrop.parcel.storage;

/** Safe storage metadata. Deliberately excludes URLs, credentials, and upload signatures. */
public record StoredDeliveryPhoto(
        String assetId,
        String publicId,
        String format,
        long byteSize,
        String resourceType
) {
}
