package com.swiftdrop.parcel.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "parcel_delivery_otp_challenges", schema = "public")
public class ParcelDeliveryOtpChallenge {
    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "parcel_id", nullable = false, length = 64)
    private String parcelId;

    @Column(name = "assignment_id", nullable = false)
    private Long assignmentId;

    @Column(name = "courier_id", nullable = false, length = 64)
    private String courierId;

    @Column(name = "code_hmac", nullable = false)
    private byte[] codeHmac;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "attempt_count", nullable = false)
    private short attemptCount;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "otp_consumed_at")
    private Instant otpConsumedAt;

    @Column(name = "invalidated_at")
    private Instant invalidatedAt;

    @Column(name = "proof_consumed_at")
    private Instant proofConsumedAt;

    protected ParcelDeliveryOtpChallenge() {
    }

    public ParcelDeliveryOtpChallenge(UUID id, String parcelId, Long assignmentId, String courierId,
                                      byte[] codeHmac, Instant createdAt, Instant expiresAt) {
        this.id = id;
        this.parcelId = parcelId;
        this.assignmentId = assignmentId;
        this.courierId = courierId;
        this.codeHmac = codeHmac.clone();
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.attemptCount = 0;
    }

    public void recordIncorrectAttempt(Instant now) {
        if (invalidatedAt != null || otpConsumedAt != null || attemptCount >= 5) {
            throw new IllegalStateException("OTP challenge is no longer verifiable");
        }
        attemptCount++;
        if (attemptCount >= 5) {
            invalidatedAt = now;
        }
    }

    public void verifyAndConsumeOtp(Instant now) {
        if (invalidatedAt != null || otpConsumedAt != null || verifiedAt != null) {
            throw new IllegalStateException("OTP challenge is no longer verifiable");
        }
        otpConsumedAt = now;
        verifiedAt = now;
    }

    public void consumeProofAt(Instant now) {
        if (verifiedAt == null || otpConsumedAt == null || invalidatedAt != null || proofConsumedAt != null) {
            throw new IllegalStateException("OTP proof is not available for delivery completion");
        }
        proofConsumedAt = now;
    }

    public void invalidateAt(Instant now) {
        if (invalidatedAt == null && proofConsumedAt == null) {
            invalidatedAt = now;
        }
    }

    public boolean isUsable() {
        return invalidatedAt == null && proofConsumedAt == null && attemptCount < 5;
    }

    public byte[] codeHmacForVerification() {
        return codeHmac.clone();
    }

    public UUID getId() { return id; }
    public String getParcelId() { return parcelId; }
    public Long getAssignmentId() { return assignmentId; }
    public String getCourierId() { return courierId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public short getAttemptCount() { return attemptCount; }
    public Instant getVerifiedAt() { return verifiedAt; }
    public Instant getOtpConsumedAt() { return otpConsumedAt; }
    public Instant getInvalidatedAt() { return invalidatedAt; }
    public Instant getProofConsumedAt() { return proofConsumedAt; }
}
