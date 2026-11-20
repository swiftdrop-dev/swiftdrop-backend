package com.swiftdrop.dispatch.dto.request;

import com.swiftdrop.dispatch.entity.enums.DispatchPriority;
import com.swiftdrop.dispatch.entity.enums.VehicleType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public class CreateDispatchJobRequest {

    @NotNull(message = "Parcel ID is required")
    private UUID parcelId;

    @NotBlank(message = "Tracking number is required")
    @Size(max = 100)
    private String trackingNumber;

    @NotNull(message = "Merchant ID is required")
    private UUID merchantId;

    private String zoneCode;

    @NotBlank(message = "Pickup street address is required")
    private String pickupStreetAddress;

    @NotBlank(message = "Pickup city is required")
    private String pickupCity;

    @NotNull(message = "Pickup latitude is required")
    private BigDecimal pickupLatitude;

    @NotNull(message = "Pickup longitude is required")
    private BigDecimal pickupLongitude;

    @NotBlank(message = "Delivery street address is required")
    private String deliveryStreetAddress;

    @NotBlank(message = "Delivery city is required")
    private String deliveryCity;

    @NotNull(message = "Delivery latitude is required")
    private BigDecimal deliveryLatitude;

    @NotNull(message = "Delivery longitude is required")
    private BigDecimal deliveryLongitude;

    @NotNull(message = "Parcel weight is required")
    @DecimalMin(value = "0.01", message = "Parcel weight must be positive")
    private BigDecimal parcelWeightKg;

    private VehicleType requiredVehicleType = VehicleType.MOTORCYCLE;

    private DispatchPriority priority = DispatchPriority.STANDARD;

    public CreateDispatchJobRequest() {
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
}
