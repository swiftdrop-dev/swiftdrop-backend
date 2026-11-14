package com.swiftdrop.dispatch.dto.response;

import com.swiftdrop.dispatch.entity.enums.VehicleType;

import java.math.BigDecimal;
import java.util.UUID;

public class CourierCandidateResponse {

    private UUID courierId;
    private String courierName;
    private String courierPhone;
    private VehicleType vehicleType;
    private BigDecimal distanceKm;
    private Integer activeDeliveriesCount;
    private Integer maxCapacity;
    private Double matchScore;

    public CourierCandidateResponse() {
    }

    public CourierCandidateResponse(UUID courierId, String courierName, String courierPhone, VehicleType vehicleType,
                                    BigDecimal distanceKm, Integer activeDeliveriesCount, Integer maxCapacity, Double matchScore) {
        this.courierId = courierId;
        this.courierName = courierName;
        this.courierPhone = courierPhone;
        this.vehicleType = vehicleType;
        this.distanceKm = distanceKm;
        this.activeDeliveriesCount = activeDeliveriesCount;
        this.maxCapacity = maxCapacity;
        this.matchScore = matchScore;
    }

    public UUID getCourierId() {
        return courierId;
    }

    public void setCourierId(UUID courierId) {
        this.courierId = courierId;
    }

    public String getCourierName() {
        return courierName;
    }

    public void setCourierName(String courierName) {
        this.courierName = courierName;
    }

    public String getCourierPhone() {
        return courierPhone;
    }

    public void setCourierPhone(String courierPhone) {
        this.courierPhone = courierPhone;
    }

    public VehicleType getVehicleType() {
        return vehicleType;
    }

    public void setVehicleType(VehicleType vehicleType) {
        this.vehicleType = vehicleType;
    }

    public BigDecimal getDistanceKm() {
        return distanceKm;
    }

    public void setDistanceKm(BigDecimal distanceKm) {
        this.distanceKm = distanceKm;
    }

    public Integer getActiveDeliveriesCount() {
        return activeDeliveriesCount;
    }

    public void setActiveDeliveriesCount(Integer activeDeliveriesCount) {
        this.activeDeliveriesCount = activeDeliveriesCount;
    }

    public Integer getMaxCapacity() {
        return maxCapacity;
    }

    public void setMaxCapacity(Integer maxCapacity) {
        this.maxCapacity = maxCapacity;
    }

    public Double getMatchScore() {
        return matchScore;
    }

    public void setMatchScore(Double matchScore) {
        this.matchScore = matchScore;
    }
}
