package com.swiftdrop.dispatch.entity;

import com.swiftdrop.dispatch.entity.enums.VehicleType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "courier_status_cache")
public class CourierStatusCache {

    @Id
    @Column(name = "courier_id", nullable = false)
    private UUID courierId;

    @Column(name = "courier_name", nullable = false)
    private String courierName;

    @Column(name = "courier_phone", nullable = false, length = 32)
    private String courierPhone;

    @Enumerated(EnumType.STRING)
    @Column(name = "vehicle_type", nullable = false, length = 50)
    private VehicleType vehicleType = VehicleType.MOTORCYCLE;

    @Column(name = "is_online", nullable = false)
    private Boolean isOnline = false;

    @Column(name = "is_available", nullable = false)
    private Boolean isAvailable = false;

    @Column(name = "current_latitude", precision = 10, scale = 7)
    private BigDecimal currentLatitude;

    @Column(name = "current_longitude", precision = 10, scale = 7)
    private BigDecimal currentLongitude;

    @Column(name = "active_deliveries_count", nullable = false)
    private Integer activeDeliveriesCount = 0;

    @Column(name = "max_capacity", nullable = false)
    private Integer maxCapacity = 4;

    @Column(name = "last_location_updated_at")
    private Instant lastLocationUpdatedAt;

    @Column(name = "last_sync_at", nullable = false)
    private Instant lastSyncAt;

    @Column(name = "projection_version", nullable = false)
    private Long projectionVersion = 1L;

    public CourierStatusCache() {
    }

    @PrePersist
    protected void onCreate() {
        if (this.lastSyncAt == null) {
            this.lastSyncAt = Instant.now();
        }
        if (this.projectionVersion == null) {
            this.projectionVersion = 1L;
        }
        if (this.isOnline == null) {
            this.isOnline = false;
        }
        if (this.isAvailable == null) {
            this.isAvailable = false;
        }
        if (this.activeDeliveriesCount == null) {
            this.activeDeliveriesCount = 0;
        }
        if (this.maxCapacity == null) {
            this.maxCapacity = 4;
        }
        if (this.vehicleType == null) {
            this.vehicleType = VehicleType.MOTORCYCLE;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.lastSyncAt = Instant.now();
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

    public Instant getLastLocationUpdatedAt() {
        return lastLocationUpdatedAt;
    }

    public void setLastLocationUpdatedAt(Instant lastLocationUpdatedAt) {
        this.lastLocationUpdatedAt = lastLocationUpdatedAt;
    }

    public Instant getLastSyncAt() {
        return lastSyncAt;
    }

    public void setLastSyncAt(Instant lastSyncAt) {
        this.lastSyncAt = lastSyncAt;
    }

    public Long getProjectionVersion() {
        return projectionVersion;
    }

    public void setProjectionVersion(Long projectionVersion) {
        this.projectionVersion = projectionVersion;
    }
}
