package com.swiftdrop.tracking.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ParcelResponseDto {
    private String id;
    private String trackingNumber;
    private String senderId;
    private String status;

    public ParcelResponseDto() {
    }

    public ParcelResponseDto(String id, String trackingNumber, String senderId, String status) {
        this.id = id;
        this.trackingNumber = trackingNumber;
        this.senderId = senderId;
        this.status = status;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTrackingNumber() {
        return trackingNumber;
    }

    public void setTrackingNumber(String trackingNumber) {
        this.trackingNumber = trackingNumber;
    }

    public String getSenderId() {
        return senderId;
    }

    public void setSenderId(String senderId) {
        this.senderId = senderId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
