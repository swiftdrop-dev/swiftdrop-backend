package com.swiftdrop.parcel.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "parcel_assignments", schema = "public")
public class ParcelAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "parcel_id", nullable = false, length = 64)
    private String parcelId;

    @Column(name = "courier_id", nullable = false, length = 64)
    private String courierId;

    @Column(name = "assigned_at", nullable = false)
    private Instant assignedAt;

    @Column(name = "unassigned_at")
    private Instant unassignedAt;

    @Column(name = "changed_by", length = 128)
    private String changedBy;

    @Column(name = "reason", columnDefinition = "text")
    private String reason;

    protected ParcelAssignment() {
    }

    public ParcelAssignment(String parcelId, String courierId, Instant assignedAt,
                            String changedBy, String reason) {
        this.parcelId = parcelId;
        this.courierId = courierId;
        this.assignedAt = assignedAt;
        this.changedBy = changedBy;
        this.reason = reason;
    }

    public void closeAt(Instant unassignedAt) {
        if (this.unassignedAt != null) {
            throw new IllegalStateException("Assignment is already closed");
        }
        if (unassignedAt.isBefore(assignedAt)) {
            throw new IllegalArgumentException("Unassignment time cannot precede assignment time");
        }
        this.unassignedAt = unassignedAt;
    }

    public Long getId() { return id; }
    public String getParcelId() { return parcelId; }
    public String getCourierId() { return courierId; }
    public Instant getAssignedAt() { return assignedAt; }
    public Instant getUnassignedAt() { return unassignedAt; }
    public String getChangedBy() { return changedBy; }
    public String getReason() { return reason; }
}
