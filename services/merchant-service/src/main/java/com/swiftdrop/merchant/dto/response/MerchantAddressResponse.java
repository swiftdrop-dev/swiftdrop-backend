package com.swiftdrop.merchant.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

public class MerchantAddressResponse {

    private UUID id;
    private String addressLabel;
    private String streetAddress;
    private String city;
    private String district;
    private String postalCode;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private String contactPhone;
    private Boolean isPrimaryPickup;
    private Boolean isActive;

    public MerchantAddressResponse() {
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getAddressLabel() {
        return addressLabel;
    }

    public void setAddressLabel(String addressLabel) {
        this.addressLabel = addressLabel;
    }

    public String getStreetAddress() {
        return streetAddress;
    }

    public void setStreetAddress(String streetAddress) {
        this.streetAddress = streetAddress;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getDistrict() {
        return district;
    }

    public void setDistrict(String district) {
        this.district = district;
    }

    public String getPostalCode() {
        return postalCode;
    }

    public void setPostalCode(String postalCode) {
        this.postalCode = postalCode;
    }

    public BigDecimal getLatitude() {
        return latitude;
    }

    public void setLatitude(BigDecimal latitude) {
        this.latitude = latitude;
    }

    public BigDecimal getLongitude() {
        return longitude;
    }

    public void setLongitude(BigDecimal longitude) {
        this.longitude = longitude;
    }

    public String getContactPhone() {
        return contactPhone;
    }

    public void setContactPhone(String contactPhone) {
        this.contactPhone = contactPhone;
    }

    public Boolean getIsPrimaryPickup() {
        return isPrimaryPickup;
    }

    public void setIsPrimaryPickup(Boolean isPrimaryPickup) {
        this.isPrimaryPickup = isPrimaryPickup;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
    }
}
