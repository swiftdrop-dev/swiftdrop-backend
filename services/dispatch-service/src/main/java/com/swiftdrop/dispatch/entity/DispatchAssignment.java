package com.swiftdrop.dispatch.entity;

import com.swiftdrop.dispatch.entity.enums.AssignmentDecision;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "dispatch_assignments")
public class DispatchAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dispatch_job_id", nullable = false)
    private DispatchJob dispatchJob;

    @Column(name = "courier_id", nullable = false)
    private UUID courierId;

    @Column(name = "attempt_number", nullable = false)
    private Integer attemptNumber = 1;

    @Enumerated(EnumType.STRING)
    @Column(name = "decision", nullable = false, length = 50)
    private AssignmentDecision decision = AssignmentDecision.OFFERED;

    @Column(name = "decision_reason", length = 255)
    private String decisionReason;

    @Column(name = "offered_distance_km", precision = 6, scale = 2)
    private BigDecimal offeredDistanceKm;

    @Column(name = "offered_at", nullable = false)
    private Instant offeredAt;

    @Column(name = "responded_at")
    private Instant respondedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public DispatchAssignment() {
    }

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (this.createdAt == null) {
            this.createdAt = now;
        }
        if (this.offeredAt == null) {
            this.offeredAt = now;
        }
        if (this.decision == null) {
            this.decision = AssignmentDecision.OFFERED;
        }
        if (this.attemptNumber == null) {
            this.attemptNumber = 1;
        }
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public DispatchJob getDispatchJob() {
        return dispatchJob;
    }

    public void setDispatchJob(DispatchJob dispatchJob) {
        this.dispatchJob = dispatchJob;
    }

    public UUID getCourierId() {
        return courierId;
    }

    public void setCourierId(UUID courierId) {
        this.courierId = courierId;
    }

    public Integer getAttemptNumber() {
        return attemptNumber;
    }

    public void setAttemptNumber(Integer attemptNumber) {
        this.attemptNumber = attemptNumber;
    }

    public AssignmentDecision getDecision() {
        return decision;
    }

    public void setDecision(AssignmentDecision decision) {
        this.decision = decision;
    }

    public String getDecisionReason() {
        return decisionReason;
    }

    public void setDecisionReason(String decisionReason) {
        this.decisionReason = decisionReason;
    }

    public BigDecimal getOfferedDistanceKm() {
        return offeredDistanceKm;
    }

    public void setOfferedDistanceKm(BigDecimal offeredDistanceKm) {
        this.offeredDistanceKm = offeredDistanceKm;
    }

    public Instant getOfferedAt() {
        return offeredAt;
    }

    public void setOfferedAt(Instant offeredAt) {
        this.offeredAt = offeredAt;
    }

    public Instant getRespondedAt() {
        return respondedAt;
    }

    public void setRespondedAt(Instant respondedAt) {
        this.respondedAt = respondedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
