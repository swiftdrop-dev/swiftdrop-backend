package com.swiftdrop.tracking.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tracking_events", schema = "public")
public class TrackingEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "event_id", nullable = false, unique = true, length = 64)
    private String eventId;

    @Column(name = "tracking_number", nullable = false, length = 64)
    private String trackingNumber;

    @Column(name = "parcel_id", nullable = false, length = 64)
    private String parcelId;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "title", nullable = false, length = 128)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "location", length = 255)
    private String location;

    @Column(name = "courier_id", length = 64)
    private String courierId;

    @Column(name = "customer_id", length = 64)
    private String customerId;

    @Column(name = "timestamp", nullable = false)
    private Instant timestamp;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public TrackingEvent() {
    }

    public TrackingEvent(String eventId, String trackingNumber, String parcelId,
                         String eventType, String status, String title,
                         String description, String location, String courierId,
                         Instant timestamp) {
        this(eventId, trackingNumber, parcelId, eventType, status, title, description, location, courierId, null, timestamp);
    }

    public TrackingEvent(String eventId, String trackingNumber, String parcelId,
                         String eventType, String status, String title,
                         String description, String location, String courierId,
                         String customerId, Instant timestamp) {
        this.eventId = eventId;
        this.trackingNumber = trackingNumber;
        this.parcelId = parcelId;
        this.eventType = eventType;
        this.status = status;
        this.title = title;
        this.description = description;
        this.location = location;
        this.courierId = courierId;
        this.customerId = customerId;
        this.timestamp = timestamp;
    }

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
        if (this.timestamp == null) {
            this.timestamp = this.createdAt;
        }
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getTrackingNumber() {
        return trackingNumber;
    }

    public void setTrackingNumber(String trackingNumber) {
        this.trackingNumber = trackingNumber;
    }

    public String getParcelId() {
        return parcelId;
    }

    public void setParcelId(String parcelId) {
        this.parcelId = parcelId;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getCourierId() {
        return courierId;
    }

    public void setCourierId(String courierId) {
        this.courierId = courierId;
    }

    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
