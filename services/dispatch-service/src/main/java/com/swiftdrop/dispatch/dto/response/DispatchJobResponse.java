package com.swiftdrop.dispatch.dto.response;

import com.swiftdrop.dispatch.entity.enums.DispatchPriority;
import com.swiftdrop.dispatch.entity.enums.DispatchStatus;
import com.swiftdrop.dispatch.entity.enums.DispatchType;
import com.swiftdrop.dispatch.entity.enums.VehicleType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class DispatchJobResponse {

    private UUID id;
    private UUID parcelId;
    private String trackingNumber;
    private UUID merchantId;
    private String zoneCode;
    private UUID courierId;

    private String pickupStreetAddress;
    private String pickupCity;
    private BigDecimal pickupLatitude;
    private BigDecimal pickupLongitude;

    private String deliveryStreetAddress;
    private String deliveryCity;
    private BigDecimal deliveryLatitude;
    private BigDecimal deliveryLongitude;

    private BigDecimal parcelWeightKg;
    private VehicleType requiredVehicleType;
    private DispatchPriority priority;
    private DispatchType dispatchType;
    private DispatchStatus status;

    private UUID assignedBy;
    private Instant assignedAt;
    private Instant offerExpiresAt;
    private Instant acceptedAt;
    private Instant completedAt;
    private Instant cancelledAt;
    private String cancellationReason;
    private String dispatcherNotes;

    private Instant createdAt;
    private Instant updatedAt;

    private List<DispatchAssignmentResponse> assignments = new ArrayList<>();

    public DispatchJobResponse() {
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

    public String getPickupStreetAddress() {
        return pickupStreetAddress;
    }

    public void setPickupStreetAddress(String pickupStreetAddress) {
        this.pickupStreetAddress = pickupStreetAddress;
    }

    public String getPickupCity() {
        return pickupCity;
    }

    public void setPickupCity(String pickupCity) {
        this.pickupCity = pickupCity;
    }

    public BigDecimal getPickupLatitude() {
        return pickupLatitude;
    }

    public void setPickupLatitude(BigDecimal pickupLatitude) {
        this.pickupLatitude = pickupLatitude;
    }

    public BigDecimal getPickupLongitude() {
        return pickupLongitude;
    }

    public void setPickupLongitude(BigDecimal pickupLongitude) {
        this.pickupLongitude = pickupLongitude;
    }

    public String getDeliveryStreetAddress() {
        return deliveryStreetAddress;
    }

    public void setDeliveryStreetAddress(String deliveryStreetAddress) {
        this.deliveryStreetAddress = deliveryStreetAddress;
    }

    public String getDeliveryCity() {
        return deliveryCity;
    }

    public void setDeliveryCity(String deliveryCity) {
        this.deliveryCity = deliveryCity;
    }

    public BigDecimal getDeliveryLatitude() {
        return deliveryLatitude;
    }

    public void setDeliveryLatitude(BigDecimal deliveryLatitude) {
        this.deliveryLatitude = deliveryLatitude;
    }

    public BigDecimal getDeliveryLongitude() {
        return deliveryLongitude;
    }

    public void setDeliveryLongitude(BigDecimal deliveryLongitude) {
        this.deliveryLongitude = deliveryLongitude;
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

    public UUID getAssignedBy() {
        return assignedBy;
    }

    public void setAssignedBy(UUID assignedBy) {
        this.assignedBy = assignedBy;
    }

    public Instant getAssignedAt() {
        return assignedAt;
    }

    public void setAssignedAt(Instant assignedAt) {
        this.assignedAt = assignedAt;
    }

    public Instant getOfferExpiresAt() {
        return offerExpiresAt;
    }

    public void setOfferExpiresAt(Instant offerExpiresAt) {
        this.offerExpiresAt = offerExpiresAt;
    }

    public Instant getAcceptedAt() {
        return acceptedAt;
    }

    public void setAcceptedAt(Instant acceptedAt) {
        this.acceptedAt = acceptedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }

    public void setCancelledAt(Instant cancelledAt) {
        this.cancelledAt = cancelledAt;
    }

    public String getCancellationReason() {
        return cancellationReason;
    }

    public void setCancellationReason(String cancellationReason) {
        this.cancellationReason = cancellationReason;
    }

    public String getDispatcherNotes() {
        return dispatcherNotes;
    }

    public void setDispatcherNotes(String dispatcherNotes) {
        this.dispatcherNotes = dispatcherNotes;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public List<DispatchAssignmentResponse> getAssignments() {
        return assignments;
    }

    public void setAssignments(List<DispatchAssignmentResponse> assignments) {
        this.assignments = assignments;
    }
}
