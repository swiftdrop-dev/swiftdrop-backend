package com.swiftdrop.merchant.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "merchants", uniqueConstraints = @UniqueConstraint(name = "uk_merchants_owner", columnNames = "owner_subject"))
public class Merchant {
    @Id private UUID id;
    @Column(name = "owner_subject", nullable = false, length = 255, updatable = false) private String ownerSubject;
    @Column(name = "business_name", nullable = false, length = 200) private String businessName;
    @Column(name = "registration_number", nullable = false, length = 100) private String registrationNumber;
    @Column(name = "contact_name", nullable = false, length = 200) private String contactName;
    @Column(name = "contact_email", nullable = false, length = 254) private String contactEmail;
    @Column(name = "contact_phone", nullable = false, length = 32) private String contactPhone;
    @Column(name = "pickup_street", nullable = false, length = 300) private String pickupStreet;
    @Column(name = "pickup_city", nullable = false, length = 100) private String pickupCity;
    @Column(name = "pickup_district", nullable = false, length = 100) private String pickupDistrict;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private MerchantStatus status;
    @Column(name = "reviewed_by", length = 255) private String reviewedBy;
    @Column(name = "review_reason", length = 1000) private String reviewReason;
    @Column(name = "reviewed_at") private Instant reviewedAt;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Version private Long version;

    protected Merchant() {}
    public Merchant(String ownerSubject, String businessName, String registrationNumber,
                    String contactName, String contactEmail, String contactPhone,
                    String pickupStreet, String pickupCity, String pickupDistrict) {
        this.id = UUID.randomUUID(); this.ownerSubject = ownerSubject;
        this.businessName = businessName; this.registrationNumber = registrationNumber;
        updateProfile(contactName, contactEmail, contactPhone, pickupStreet, pickupCity, pickupDistrict);
        this.status = MerchantStatus.PENDING; this.createdAt = Instant.now(); this.updatedAt = createdAt;
    }
    public void updateProfile(String name, String email, String phone, String street, String city, String district) {
        this.contactName = name; this.contactEmail = email; this.contactPhone = phone;
        this.pickupStreet = street; this.pickupCity = city; this.pickupDistrict = district;
        this.updatedAt = Instant.now();
    }
    public void review(MerchantStatus decision, String reviewer, String reason) {
        if (status != MerchantStatus.PENDING || decision == null || decision == MerchantStatus.PENDING)
            throw new IllegalStateException("Only pending applications may be approved or rejected");
        this.status = decision; this.reviewedBy = reviewer; this.reviewReason = reason;
        this.reviewedAt = Instant.now(); this.updatedAt = reviewedAt;
    }
    public UUID getId() { return id; }
    public String getOwnerSubject() { return ownerSubject; }
    public String getBusinessName() { return businessName; }
    public String getRegistrationNumber() { return registrationNumber; }
    public String getContactName() { return contactName; }
    public String getContactEmail() { return contactEmail; }
    public String getContactPhone() { return contactPhone; }
    public String getPickupStreet() { return pickupStreet; }
    public String getPickupCity() { return pickupCity; }
    public String getPickupDistrict() { return pickupDistrict; }
    public MerchantStatus getStatus() { return status; }
    public String getReviewReason() { return reviewReason; }
    public Instant getReviewedAt() { return reviewedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
