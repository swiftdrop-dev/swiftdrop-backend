package com.swiftdrop.parcel.storage;

/** Storage boundary for delivery photo bytes. Provider configuration stays behind this interface. */
public interface DeliveryPhotoStorage {

    StoredDeliveryPhoto upload(byte[] imageBytes);

    /** Deletes by immutable provider asset identifier. Repeated deletion is safe. */
    void deleteByAssetId(String assetId);
}
