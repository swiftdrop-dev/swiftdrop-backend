package com.swiftdrop.parcel.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.DynamicUpdate;

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

    @Column(name = "estimated_delivery_date", length = 64)
    private String estimatedDeliveryDate;

    @Column(name = "recipient_name", length = 255)
    private String recipientName;

    @Column(name = "recipient_phone", length = 32)
    private String recipientPhone;

    @Column(name = "recipient_address", length = 500)
    private String recipientAddress;

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
    public String getEstimatedDeliveryDate() { return estimatedDeliveryDate; }
    public String getRecipientName() { return recipientName; }
    public String getRecipientPhone() { return recipientPhone; }
    public String getRecipientAddress() { return recipientAddress; }

    public void assignTo(String courierId) {
        this.courierId = courierId;
    }
}
