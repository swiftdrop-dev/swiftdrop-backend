package com.swiftdrop.gateway.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "parcels")
public class ParcelEntity {

    @Id
    @Column(name = "id", length = 64)
    private String id;

    @Column(name = "tracking_number", unique = true, nullable = false, length = 64)
    private String trackingNumber;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "sender_id", length = 64)
    private String senderId;

    @Column(name = "sender_name")
    private String senderName;

    @Column(name = "sender_phone", length = 32)
    private String senderPhone;

    @Column(name = "sender_type", length = 32)
    private String senderType;

    @Column(name = "recipient_name")
    private String recipientName;

    @Column(name = "recipient_phone", length = 32)
    private String recipientPhone;

    @Column(name = "recipient_address", length = 500)
    private String recipientAddress;

    @Column(name = "pickup_address", length = 500)
    private String pickupAddress;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "weight")
    private Double weight;

    @Column(name = "size", length = 16)
    private String size;

    @Column(name = "delivery_fee")
    private Double deliveryFee;

    @Column(name = "total_fee")
    private Double totalFee;

    @Column(name = "payment_status", length = 32)
    private String paymentStatus;

    @Column(name = "payment_method", length = 32)
    private String paymentMethod;

    @Column(name = "courier_id", length = 64)
    private String courierId;

    @Column(name = "courier_name")
    private String courierName;

    @Column(name = "courier_phone", length = 32)
    private String courierPhone;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;

    @Column(name = "estimated_delivery_date", length = 64)
    private String estimatedDeliveryDate;

    public ParcelEntity() {
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTrackingNumber() { return trackingNumber; }
    public void setTrackingNumber(String trackingNumber) { this.trackingNumber = trackingNumber; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getSenderId() { return senderId; }
    public void setSenderId(String senderId) { this.senderId = senderId; }

    public String getSenderName() { return senderName; }
    public void setSenderName(String senderName) { this.senderName = senderName; }

    public String getSenderPhone() { return senderPhone; }
    public void setSenderPhone(String senderPhone) { this.senderPhone = senderPhone; }

    public String getSenderType() { return senderType; }
    public void setSenderType(String senderType) { this.senderType = senderType; }

    public String getRecipientName() { return recipientName; }
    public void setRecipientName(String recipientName) { this.recipientName = recipientName; }

    public String getRecipientPhone() { return recipientPhone; }
    public void setRecipientPhone(String recipientPhone) { this.recipientPhone = recipientPhone; }

    public String getRecipientAddress() { return recipientAddress; }
    public void setRecipientAddress(String recipientAddress) { this.recipientAddress = recipientAddress; }

    public String getPickupAddress() { return pickupAddress; }
    public void setPickupAddress(String pickupAddress) { this.pickupAddress = pickupAddress; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Double getWeight() { return weight; }
    public void setWeight(Double weight) { this.weight = weight; }

    public String getSize() { return size; }
    public void setSize(String size) { this.size = size; }

    public Double getDeliveryFee() { return deliveryFee; }
    public void setDeliveryFee(Double deliveryFee) { this.deliveryFee = deliveryFee; }

    public Double getTotalFee() { return totalFee; }
    public void setTotalFee(Double totalFee) { this.totalFee = totalFee; }

    public String getPaymentStatus() { return paymentStatus; }
    public void setPaymentStatus(String paymentStatus) { this.paymentStatus = paymentStatus; }

    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }

    public String getCourierId() { return courierId; }
    public void setCourierId(String courierId) { this.courierId = courierId; }

    public String getCourierName() { return courierName; }
    public void setCourierName(String courierName) { this.courierName = courierName; }

    public String getCourierPhone() { return courierPhone; }
    public void setCourierPhone(String courierPhone) { this.courierPhone = courierPhone; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getDeliveredAt() { return deliveredAt; }
    public void setDeliveredAt(LocalDateTime deliveredAt) { this.deliveredAt = deliveredAt; }

    public String getEstimatedDeliveryDate() { return estimatedDeliveryDate; }
    public void setEstimatedDeliveryDate(String estimatedDeliveryDate) { this.estimatedDeliveryDate = estimatedDeliveryDate; }
}
