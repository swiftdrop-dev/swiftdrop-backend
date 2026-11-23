package com.swiftdrop.merchant.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Entity
@Table(name = "merchant_webhook_endpoints")
public class WebhookEndpoint {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "merchant_id", nullable = false)
    private Merchant merchant;

    @Column(name = "url", nullable = false, length = 1024)
    private String url;

    @Column(name = "secret", nullable = false, length = 255)
    private String secret;

    @Column(name = "description", length = 255)
    private String description;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    @Column(name = "subscribed_events", nullable = false, length = 500)
    private String subscribedEvents = "PARCEL_CREATED,PARCEL_ASSIGNED,PARCEL_PICKED_UP,PARCEL_IN_TRANSIT,PARCEL_OUT_FOR_DELIVERY,PARCEL_DELIVERY_FAILED,PARCEL_DELIVERED";

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public WebhookEndpoint() {
    }

    public WebhookEndpoint(Merchant merchant, String url, String secret, String description, List<String> subscribedEvents) {
        this.merchant = merchant;
        this.url = url;
        this.secret = secret;
        this.description = description;
        if (subscribedEvents != null && !subscribedEvents.isEmpty()) {
            this.subscribedEvents = String.join(",", subscribedEvents);
        }
    }

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (this.createdAt == null) {
            this.createdAt = now;
        }
        if (this.updatedAt == null) {
            this.updatedAt = now;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public boolean subscribesTo(String eventType) {
        if (!isActive || deletedAt != null || eventType == null) {
            return false;
        }
        if ("*".equals(subscribedEvents.trim())) {
            return true;
        }
        Set<String> events = Arrays.stream(subscribedEvents.split(","))
                .map(String::trim)
                .map(String::toUpperCase)
                .collect(Collectors.toSet());
        return events.contains(eventType.trim().toUpperCase());
    }

    public List<String> getSubscribedEventsList() {
        if (subscribedEvents == null || subscribedEvents.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.stream(subscribedEvents.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toList();
    }

    public void setSubscribedEventsList(List<String> events) {
        if (events == null || events.isEmpty()) {
            this.subscribedEvents = "PARCEL_CREATED,PARCEL_ASSIGNED,PARCEL_PICKED_UP,PARCEL_IN_TRANSIT,PARCEL_OUT_FOR_DELIVERY,PARCEL_DELIVERY_FAILED,PARCEL_DELIVERED";
        } else {
            this.subscribedEvents = String.join(",", events);
        }
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Merchant getMerchant() {
        return merchant;
    }

    public void setMerchant(Merchant merchant) {
        this.merchant = merchant;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isActive() {
        return isActive;
    }

    public void setActive(boolean active) {
        isActive = active;
    }

    public String getSubscribedEvents() {
        return subscribedEvents;
    }

    public void setSubscribedEvents(String subscribedEvents) {
        this.subscribedEvents = subscribedEvents;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(Instant deletedAt) {
        this.deletedAt = deletedAt;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }
}
