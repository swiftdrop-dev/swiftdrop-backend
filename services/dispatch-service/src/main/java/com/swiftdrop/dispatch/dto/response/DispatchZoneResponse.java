package com.swiftdrop.dispatch.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public class DispatchZoneResponse {

    private UUID id;
    private String zoneCode;
    private String zoneName;
    private String description;
    private BigDecimal centerLatitude;
    private BigDecimal centerLongitude;
    private BigDecimal radiusKm;
    private UUID assignedDispatcherId;
    private Boolean isActive;
    private Instant createdAt;

    public DispatchZoneResponse() {
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getZoneCode() {
        return zoneCode;
    }

    public void setZoneCode(String zoneCode) {
        this.zoneCode = zoneCode;
    }

    public String getZoneName() {
        return zoneName;
    }

    public void setZoneName(String zoneName) {
        this.zoneName = zoneName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getCenterLatitude() {
        return centerLatitude;
    }

    public void setCenterLatitude(BigDecimal centerLatitude) {
        this.centerLatitude = centerLatitude;
    }

    public BigDecimal getCenterLongitude() {
        return centerLongitude;
    }

    public void setCenterLongitude(BigDecimal centerLongitude) {
        this.centerLongitude = centerLongitude;
    }

    public BigDecimal getRadiusKm() {
        return radiusKm;
    }

    public void setRadiusKm(BigDecimal radiusKm) {
        this.radiusKm = radiusKm;
    }

    public UUID getAssignedDispatcherId() {
        return assignedDispatcherId;
    }

    public void setAssignedDispatcherId(UUID assignedDispatcherId) {
        this.assignedDispatcherId = assignedDispatcherId;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
