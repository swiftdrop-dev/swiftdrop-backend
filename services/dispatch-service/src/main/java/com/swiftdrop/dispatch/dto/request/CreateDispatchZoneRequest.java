package com.swiftdrop.dispatch.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public class CreateDispatchZoneRequest {

    @NotBlank(message = "Zone code is required (e.g. CMB-CENTRAL)")
    @Size(max = 50)
    private String zoneCode;

    @NotBlank(message = "Zone name is required")
    @Size(max = 150)
    private String zoneName;

    @Size(max = 500)
    private String description;

    @NotNull(message = "Center latitude is required")
    @DecimalMin(value = "-90.0")
    @DecimalMax(value = "90.0")
    private BigDecimal centerLatitude;

    @NotNull(message = "Center longitude is required")
    @DecimalMin(value = "-180.0")
    @DecimalMax(value = "180.0")
    private BigDecimal centerLongitude;

    private BigDecimal radiusKm = new BigDecimal("10.00");

    private UUID assignedDispatcherId;

    public CreateDispatchZoneRequest() {
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
}
