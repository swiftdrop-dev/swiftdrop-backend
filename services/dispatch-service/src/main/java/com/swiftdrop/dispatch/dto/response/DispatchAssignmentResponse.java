package com.swiftdrop.dispatch.dto.response;

import com.swiftdrop.dispatch.entity.enums.AssignmentDecision;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public class DispatchAssignmentResponse {

    private UUID id;
    private UUID dispatchJobId;
    private UUID courierId;
    private Integer attemptNumber;
    private AssignmentDecision decision;
    private String decisionReason;
    private BigDecimal offeredDistanceKm;
    private Instant offeredAt;
    private Instant respondedAt;

    public DispatchAssignmentResponse() {
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getDispatchJobId() {
        return dispatchJobId;
    }

    public void setDispatchJobId(UUID dispatchJobId) {
        this.dispatchJobId = dispatchJobId;
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
}
