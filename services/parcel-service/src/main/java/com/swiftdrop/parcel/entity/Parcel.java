package com.swiftdrop.parcel.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.DynamicUpdate;

import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "parcels", schema = "public")
@DynamicUpdate
public class Parcel {

    @Id
    @Column(name = "id", nullable = false, length = 64)
    private String id;

    @Column(name = "courier_id", length = 64)
    private String courierId;

    @Column(name = "tracking_number", nullable = false, unique = true, length = 64)
    private String trackingNumber;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    /**
     * Existing PostgreSQL TIMESTAMP WITHOUT TIME ZONE column. New writes, when
     * delivery is enabled, must use the documented UTC-naive convention.
     */
    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;

    @Column(name = "estimated_delivery_date", length = 64)
    private String estimatedDeliveryDate;

    @Column(name = "recipient_name", length = 255)
    private String recipientName;

    @Column(name = "recipient_phone", length = 32)
    private String recipientPhone;

    @Column(name = "recipient_address", length = 500)
    private String recipientAddress;

    @Column(name = "sender_id", length = 64)
    private String senderId;

    @Column(name = "sender_name", length = 255)
    private String senderName;

    @Column(name = "sender_phone", length = 32)
    private String senderPhone;

    @Column(name = "pickup_address", length = 500)
    private String pickupAddress;

    @Column(name = "weight_kg", precision = 6, scale = 2)
    private java.math.BigDecimal weightKg;

    @Column(name = "parcel_size", length = 32)
    private String parcelSize;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "fragile")
    private Boolean fragile;

    @Column(name = "total_fee", precision = 10, scale = 2)
    private java.math.BigDecimal totalFee;

    @Column(name = "payment_method", length = 32)
    private String paymentMethod;

    @Column(name = "payment_status", length = 32)
    private String paymentStatus;

    @Column(name = "created_at")
    private java.time.Instant createdAt;

    @Column(name = "updated_at")
    private java.time.Instant updatedAt;

    @jakarta.persistence.PrePersist
    public void onPrePersist() {
        if (createdAt == null) {
            createdAt = java.time.Instant.now();
        }
        if (updatedAt == null) {
            updatedAt = createdAt;
        }
    }

    @jakarta.persistence.PreUpdate
    public void onPreUpdate() {
        updatedAt = java.time.Instant.now();
    }

    protected Parcel() {
    }

    public Parcel(String id, String courierId) {
        this.id = id;
        this.courierId = courierId;
    }

    public Parcel(String id, String courierId, String trackingNumber, String status,
                  String estimatedDeliveryDate, String recipientName,
                  String recipientPhone, String recipientAddress) {
        this.id = id;
        this.courierId = courierId;
        this.trackingNumber = trackingNumber;
        this.status = status;
        this.estimatedDeliveryDate = estimatedDeliveryDate;
        this.recipientName = recipientName;
        this.recipientPhone = recipientPhone;
        this.recipientAddress = recipientAddress;
    }

    public String getId() { return id; }
    public String getCourierId() { return courierId; }
    public String getTrackingNumber() { return trackingNumber; }
    public String getStatus() { return status; }
    public LocalDateTime getDeliveredAt() { return deliveredAt; }
    public String getEstimatedDeliveryDate() { return estimatedDeliveryDate; }
    public String getRecipientName() { return recipientName; }
    public String getRecipientPhone() { return recipientPhone; }
    public String getRecipientAddress() { return recipientAddress; }
    public String getSenderId() { return senderId; }
    public String getSenderName() { return senderName; }
    public String getSenderPhone() { return senderPhone; }
    public String getPickupAddress() { return pickupAddress; }
    public java.math.BigDecimal getWeightKg() { return weightKg; }
    public String getParcelSize() { return parcelSize; }
    public String getDescription() { return description; }
    public Boolean getFragile() { return fragile; }
    public java.math.BigDecimal getTotalFee() { return totalFee; }
    public String getPaymentMethod() { return paymentMethod; }
    public String getPaymentStatus() { return paymentStatus; }
    public java.time.Instant getCreatedAt() { return createdAt; }
    public java.time.Instant getUpdatedAt() { return updatedAt; }

    public void setSenderId(String senderId) { this.senderId = senderId; }
    public void setSenderName(String senderName) { this.senderName = senderName; }
    public void setSenderPhone(String senderPhone) { this.senderPhone = senderPhone; }
    public void setPickupAddress(String pickupAddress) { this.pickupAddress = pickupAddress; }
    public void setWeightKg(java.math.BigDecimal weightKg) { this.weightKg = weightKg; }
    public void setParcelSize(String parcelSize) { this.parcelSize = parcelSize; }
    public void setDescription(String description) { this.description = description; }
    public void setFragile(Boolean fragile) { this.fragile = fragile; }
    public void setTotalFee(java.math.BigDecimal totalFee) { this.totalFee = totalFee; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }
    public void setPaymentStatus(String paymentStatus) { this.paymentStatus = paymentStatus; }
    public void setEstimatedDeliveryDate(String estimatedDeliveryDate) { this.estimatedDeliveryDate = estimatedDeliveryDate; }
    public void setRecipientName(String recipientName) { this.recipientName = recipientName; }
    public void setRecipientPhone(String recipientPhone) { this.recipientPhone = recipientPhone; }
    public void setRecipientAddress(String recipientAddress) { this.recipientAddress = recipientAddress; }
    public void setTrackingNumber(String trackingNumber) { this.trackingNumber = trackingNumber; }
    public void setStatus(String status) { this.status = status; }
    public void setCreatedAt(java.time.Instant createdAt) { this.createdAt = createdAt; }
    public void setUpdatedAt(java.time.Instant updatedAt) { this.updatedAt = updatedAt; }

    public void assignTo(String courierId) {
        this.courierId = courierId;
    }

    public void changeStatus(String status) {
        this.status = status;
    }

    public void completeDelivery(LocalDateTime deliveredAt) {
        this.status = "delivered";
        this.deliveredAt = Objects.requireNonNull(deliveredAt, "deliveredAt");
    }
}

