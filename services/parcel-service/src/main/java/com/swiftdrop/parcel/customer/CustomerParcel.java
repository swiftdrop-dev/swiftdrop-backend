package com.swiftdrop.parcel.customer;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "customer_parcels", indexes = @Index(name = "idx_customer_parcel_sender_created", columnList = "sender_id,created_at"))
public class CustomerParcel {
    @Id @Column(length = 36) String id;
    @Version long version;
    @Column(name = "tracking_number", nullable = false, unique = true, length = 64) String trackingNumber;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32) ParcelStatus status;
    @Column(name = "sender_id", nullable = false, length = 100) String senderId;
    @Column(length = 100) String senderName;
    @Column(length = 32) String senderPhone;
    @Column(nullable = false, length = 100) String recipientName;
    @Column(nullable = false, length = 32) String recipientPhone;
    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name="street", column=@Column(name="pickup_street", length=200)),
        @AttributeOverride(name="city", column=@Column(name="pickup_city", length=100)),
        @AttributeOverride(name="district", column=@Column(name="pickup_district", length=100)),
        @AttributeOverride(name="postalCode", column=@Column(name="pickup_postal_code", length=5))
    }) StoredAddress pickupAddress;
    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name="street", column=@Column(name="recipient_street", length=200)),
        @AttributeOverride(name="city", column=@Column(name="recipient_city", length=100)),
        @AttributeOverride(name="district", column=@Column(name="recipient_district", length=100)),
        @AttributeOverride(name="postalCode", column=@Column(name="recipient_postal_code", length=5))
    }) StoredAddress recipientAddress;
    @Column(nullable = false, length = 500) String description;
    @Column(nullable = false, precision = 7, scale = 3) BigDecimal weight;
    @Column(nullable = false, length = 2) String size;
    @Column(precision = 12, scale = 2) BigDecimal declaredValue;
    boolean fragile;
    @Column(length = 1000) String specialInstructions;
    @Column(nullable = false, precision = 12, scale = 2) BigDecimal totalFee;
    @Column(nullable = false, length = 16) String paymentMethod;
    @Column(nullable = false, length = 16) String paymentStatus;
    @Column(name = "created_at", nullable = false) Instant createdAt;
    LocalDate estimatedDeliveryDate;
    Integer rating;
    @Column(length = 1000) String feedbackComment;
    Instant feedbackSubmittedAt;
    @ElementCollection
    @CollectionTable(name = "customer_parcel_history", joinColumns = @JoinColumn(name = "parcel_id"))
    @OrderColumn(name = "event_order")
    List<HistoryEntry> history = new ArrayList<>();

    protected CustomerParcel() {}

    @Embeddable
    public static class HistoryEntry {
        @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32) ParcelStatus status;
        @Column(nullable = false) Instant timestamp;
        @Column(length = 500) String note;
        protected HistoryEntry() {}
        HistoryEntry(ParcelStatus status, Instant timestamp, String note) {
            this.status = status; this.timestamp = timestamp; this.note = note;
        }
    }
}
