package com.swiftdrop.gateway.model;

import java.util.List;

public class ParcelRecord {

    private String id;
    private String trackingNumber;
    private String status;
    private String senderId;
    private String senderName;
    private String senderPhone;
    private String recipientName;
    private String recipientPhone;
    private String recipientAddress;
    private String pickupAddress;
    private String description;
    private double weight;
    private double totalFee;
    private String paymentStatus;
    private String courierName;
    private String courierPhone;
    private String estimatedDeliveryDate;
    private List<String> statusHistory;

    public ParcelRecord() {
    }

    public ParcelRecord(String id, String trackingNumber, String status, String senderId, String senderName,
                        String senderPhone, String recipientName, String recipientPhone, String recipientAddress,
                        String pickupAddress, String description, double weight, double totalFee,
                        String paymentStatus, String courierName, String courierPhone,
                        String estimatedDeliveryDate, List<String> statusHistory) {
        this.id = id;
        this.trackingNumber = trackingNumber;
        this.status = status;
        this.senderId = senderId;
        this.senderName = senderName;
        this.senderPhone = senderPhone;
        this.recipientName = recipientName;
        this.recipientPhone = recipientPhone;
        this.recipientAddress = recipientAddress;
        this.pickupAddress = pickupAddress;
        this.description = description;
        this.weight = weight;
        this.totalFee = totalFee;
        this.paymentStatus = paymentStatus;
        this.courierName = courierName;
        this.courierPhone = courierPhone;
        this.estimatedDeliveryDate = estimatedDeliveryDate;
        this.statusHistory = statusHistory;
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

    public double getWeight() { return weight; }
    public void setWeight(double weight) { this.weight = weight; }

    public double getTotalFee() { return totalFee; }
    public void setTotalFee(double totalFee) { this.totalFee = totalFee; }

    public String getPaymentStatus() { return paymentStatus; }
    public void setPaymentStatus(String paymentStatus) { this.paymentStatus = paymentStatus; }

    public String getCourierName() { return courierName; }
    public void setCourierName(String courierName) { this.courierName = courierName; }

    public String getCourierPhone() { return courierPhone; }
    public void setCourierPhone(String courierPhone) { this.courierPhone = courierPhone; }

    public String getEstimatedDeliveryDate() { return estimatedDeliveryDate; }
    public void setEstimatedDeliveryDate(String estimatedDeliveryDate) { this.estimatedDeliveryDate = estimatedDeliveryDate; }

    public List<String> getStatusHistory() { return statusHistory; }
    public void setStatusHistory(List<String> statusHistory) { this.statusHistory = statusHistory; }
}
