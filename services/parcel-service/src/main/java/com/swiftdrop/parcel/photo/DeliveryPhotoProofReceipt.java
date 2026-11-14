package com.swiftdrop.parcel.photo;

import java.time.Instant;
import java.util.UUID;

/** Safe internal receipt that contains no Cloudinary URL, public ID, or credential. */
public record DeliveryPhotoProofReceipt(UUID proofId, String format, long byteSize, Instant uploadedAt) {
}
