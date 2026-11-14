package com.swiftdrop.dispatch.dto.request;

import com.swiftdrop.dispatch.entity.enums.VehicleType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public class UpdateCourierStatusProjectionRequest {

    @NotNull(message = "Courier ID is required")
    private UUID courierId;

    @NotBlank(message = "Courier name is required")
    private String courierName;

    @NotBlank(message = "Courier phone is required")
    private String courierPhone;

    private VehicleType vehicleType = VehicleType.MOTORCYCLE;

    private Boolean isOnline;

    private Boolean isAvailable;

    private BigDecimal currentLatitude;

    private BigDecimal currentLongitude;

    private Integer activeDeliveriesCount;

    private Integer maxCapacity;

    private Long projectionVersion;

    public UpdateCourierStatusProjectionRequest() {
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

    public Boolean getIsOnline() {
        return isOnline;
    }

    public void setIsOnline(Boolean isOnline) {
        this.isOnline = isOnline;
    }

    public Boolean getIsAvailable() {
        return isAvailable;
    }

    public void setIsAvailable(Boolean isAvailable) {
        this.isAvailable = isAvailable;
    }

    public BigDecimal getCurrentLatitude() {
        return currentLatitude;
    }

    public void setCurrentLatitude(BigDecimal currentLatitude) {
        this.currentLatitude = currentLatitude;
    }

    public BigDecimal getCurrentLongitude() {
        return currentLongitude;
    }

    public void setCurrentLongitude(BigDecimal currentLongitude) {
        this.currentLongitude = currentLongitude;
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

    public Long getProjectionVersion() {
        return projectionVersion;
    }

    public void setProjectionVersion(Long projectionVersion) {
        this.projectionVersion = projectionVersion;
    }
}
