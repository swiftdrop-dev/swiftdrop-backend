package com.swiftdrop.parcel.photo;

public record ValidatedDeliveryPhoto(String format, String contentType, long byteSize, byte[] sha256Digest) {
    public ValidatedDeliveryPhoto {
        sha256Digest = sha256Digest.clone();
    }

    @Override
    public byte[] sha256Digest() {
        return sha256Digest.clone();
    }
}
