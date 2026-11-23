package com.swiftdrop.parcel.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.Checks;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "parcel_delivery_photo_proofs", schema = "public",
        uniqueConstraints = @UniqueConstraint(name = "uq_parcel_photo_proof_asset",
                columnNames = "cloudinary_asset_id"))
@Checks({
        @Check(name = "chk_parcel_photo_proof_consumed_verified",
                constraints = "consumed_at IS NULL OR verified_at IS NOT NULL"),
        @Check(name = "chk_parcel_photo_proof_not_consumed_and_invalidated",
                constraints = "consumed_at IS NULL OR invalidated_at IS NULL"),
        @Check(name = "chk_parcel_photo_proof_lifecycle_timestamps",
                constraints = "verified_at >= uploaded_at AND "
                        + "(consumed_at IS NULL OR (consumed_at >= uploaded_at AND consumed_at >= verified_at)) AND "
                        + "(invalidated_at IS NULL OR invalidated_at >= uploaded_at)")
})
public class ParcelDeliveryPhotoProof {
    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "parcel_id", nullable = false, length = 64)
    private String parcelId;

    @Column(name = "assignment_id", nullable = false)
    private Long assignmentId;

    @Column(name = "courier_id", nullable = false, length = 64)
    private String courierId;

    @Column(name = "cloudinary_asset_id", nullable = false, length = 128)
    private String cloudinaryAssetId;

    @Column(name = "cloudinary_public_id", nullable = false, length = 255)
    private String cloudinaryPublicId;

    @Column(name = "verified_format", nullable = false, length = 8)
    private String verifiedFormat;

    @Column(name = "content_type", nullable = false, length = 32)
    private String contentType;

    @Column(name = "byte_size", nullable = false)
    private long byteSize;

    @Column(name = "sha256_digest")
    private byte[] sha256Digest;

    @Column(name = "uploaded_at", nullable = false)
    private Instant uploadedAt;

    @Column(name = "verified_at", nullable = false)
    private Instant verifiedAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @Column(name = "invalidated_at")
    private Instant invalidatedAt;

    protected ParcelDeliveryPhotoProof() {
    }

    public ParcelDeliveryPhotoProof(UUID id, String parcelId, Long assignmentId, String courierId,
                                    String cloudinaryAssetId, String cloudinaryPublicId,
                                    String verifiedFormat, String contentType, long byteSize,
                                    byte[] sha256Digest, Instant uploadedAt) {
        this.id = id;
        this.parcelId = parcelId;
        this.assignmentId = assignmentId;
        this.courierId = courierId;
        this.cloudinaryAssetId = cloudinaryAssetId;
        this.cloudinaryPublicId = cloudinaryPublicId;
        this.verifiedFormat = verifiedFormat;
        this.contentType = contentType;
        this.byteSize = byteSize;
        this.sha256Digest = sha256Digest == null ? null : sha256Digest.clone();
        this.uploadedAt = uploadedAt;
        this.verifiedAt = uploadedAt;
    }

    public void invalidateAt(Instant at) {
        if (consumedAt == null && invalidatedAt == null) {
            invalidatedAt = at;
        }
    }

    public void consumeAt(Instant at) {
        if (verifiedAt == null || consumedAt != null || invalidatedAt != null) {
            throw new IllegalStateException("Photo proof is not available for delivery completion");
        }
        consumedAt = at;
    }

    public UUID getId() { return id; }
    public String getParcelId() { return parcelId; }
    public Long getAssignmentId() { return assignmentId; }
    public String getCourierId() { return courierId; }
    public String getCloudinaryAssetId() { return cloudinaryAssetId; }
    public String getCloudinaryPublicId() { return cloudinaryPublicId; }
    public String getVerifiedFormat() { return verifiedFormat; }
    public String getContentType() { return contentType; }
    public long getByteSize() { return byteSize; }
    public byte[] getSha256Digest() { return sha256Digest == null ? null : sha256Digest.clone(); }
    public Instant getUploadedAt() { return uploadedAt; }
    public Instant getVerifiedAt() { return verifiedAt; }
    public Instant getConsumedAt() { return consumedAt; }
    public Instant getInvalidatedAt() { return invalidatedAt; }
}
