package com.swiftdrop.parcel.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "parcel_status_history", schema = "public")
public class ParcelStatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "parcel_id", nullable = false, length = 64)
    private String parcelId;

    @Column(name = "previous_status", nullable = false, length = 32)
    private String previousStatus;

    @Column(name = "new_status", nullable = false, length = 32)
    private String newStatus;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;

    @Column(name = "actor_id", nullable = false, length = 128)
    private String actorId;

    @Column(name = "failure_reason", columnDefinition = "text")
    private String failureReason;

    protected ParcelStatusHistory() {
    }

    public ParcelStatusHistory(String parcelId, String previousStatus, String newStatus,
                               Instant changedAt, String actorId, String failureReason) {
        this.parcelId = parcelId;
        this.previousStatus = previousStatus;
        this.newStatus = newStatus;
        this.changedAt = changedAt;
        this.actorId = actorId;
        this.failureReason = failureReason;
    }

    public Long getId() { return id; }
    public String getParcelId() { return parcelId; }
    public String getPreviousStatus() { return previousStatus; }
    public String getNewStatus() { return newStatus; }
    public Instant getChangedAt() { return changedAt; }
    public String getActorId() { return actorId; }
    public String getFailureReason() { return failureReason; }
}
