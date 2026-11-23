package com.swiftdrop.dispatch.dto.response;

import com.swiftdrop.dispatch.entity.enums.DispatchPriority;
import com.swiftdrop.dispatch.entity.enums.DispatchStatus;
import com.swiftdrop.dispatch.entity.enums.DispatchType;
import com.swiftdrop.dispatch.entity.enums.VehicleType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public class DispatchJobSummaryResponse {

    private UUID id;
    private UUID parcelId;
    private String trackingNumber;
    private UUID merchantId;
    private String zoneCode;
    private UUID courierId;
    private String pickupCity;
    private String deliveryCity;
    private BigDecimal parcelWeightKg;
    private VehicleType requiredVehicleType;
    private DispatchPriority priority;
    private DispatchType dispatchType;
    private DispatchStatus status;
    private Instant createdAt;

    public DispatchJobSummaryResponse() {
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getParcelId() {
        return parcelId;
    }

    public void setParcelId(UUID parcelId) {
        this.parcelId = parcelId;
    }

    public String getTrackingNumber() {
        return trackingNumber;
    }

    public void setTrackingNumber(String trackingNumber) {
        this.trackingNumber = trackingNumber;
    }

    public UUID getMerchantId() {
        return merchantId;
    }

    public void setMerchantId(UUID merchantId) {
        this.merchantId = merchantId;
    }

    public String getZoneCode() {
        return zoneCode;
    }

    public void setZoneCode(String zoneCode) {
        this.zoneCode = zoneCode;
    }

    public UUID getCourierId() {
        return courierId;
    }

    public void setCourierId(UUID courierId) {
        this.courierId = courierId;
    }

    public String getPickupCity() {
        return pickupCity;
    }

    public void setPickupCity(String pickupCity) {
        this.pickupCity = pickupCity;
    }

    public String getDeliveryCity() {
        return deliveryCity;
    }

    public void setDeliveryCity(String deliveryCity) {
        this.deliveryCity = deliveryCity;
    }

    public BigDecimal getParcelWeightKg() {
        return parcelWeightKg;
    }

    public void setParcelWeightKg(BigDecimal parcelWeightKg) {
        this.parcelWeightKg = parcelWeightKg;
    }

    public VehicleType getRequiredVehicleType() {
        return requiredVehicleType;
    }

    public void setRequiredVehicleType(VehicleType requiredVehicleType) {
        this.requiredVehicleType = requiredVehicleType;
    }

    public DispatchPriority getPriority() {
        return priority;
    }

    public void setPriority(DispatchPriority priority) {
        this.priority = priority;
    }

    public DispatchType getDispatchType() {
        return dispatchType;
    }

    public void setDispatchType(DispatchType dispatchType) {
        this.dispatchType = dispatchType;
    }

    public DispatchStatus getStatus() {
        return status;
    }

    public void setStatus(DispatchStatus status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
