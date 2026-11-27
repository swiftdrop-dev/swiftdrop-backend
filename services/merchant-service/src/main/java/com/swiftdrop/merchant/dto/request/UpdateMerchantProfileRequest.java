package com.swiftdrop.merchant.dto.request;

import com.swiftdrop.merchant.entity.enums.BusinessType;
import jakarta.validation.constraints.Size;

public class UpdateMerchantProfileRequest {

    @Size(max = 255)
    private String businessName;

    @Size(max = 255)
    private String tradeName;

    @Size(max = 100)
    private String taxId;

    private BusinessType businessType;

    @Size(max = 255)
    private String contactName;

    @Size(max = 255)
    private String contactEmail;

    @Size(max = 32)
    private String contactPhone;

    @Size(max = 255)
    private String pickupStreet;

    @Size(max = 120)
    private String pickupCity;

    @Size(max = 120)
    private String pickupDistrict;

    public UpdateMerchantProfileRequest() {
    }

    public String getBusinessName() {
        return businessName;
    }

    public void setBusinessName(String businessName) {
        this.businessName = businessName;
    }

    public String getTradeName() {
        return tradeName;
    }

    public void setTradeName(String tradeName) {
        this.tradeName = tradeName;
    }

    public String getTaxId() {
        return taxId;
    }

    public void setTaxId(String taxId) {
        this.taxId = taxId;
    }

    public BusinessType getBusinessType() {
        return businessType;
    }

    public void setBusinessType(BusinessType businessType) {
        this.businessType = businessType;
    }

    public String getContactName() { return contactName; }
    public void setContactName(String contactName) { this.contactName = contactName; }
    public String getContactEmail() { return contactEmail; }
    public void setContactEmail(String contactEmail) { this.contactEmail = contactEmail; }
    public String getContactPhone() { return contactPhone; }
    public void setContactPhone(String contactPhone) { this.contactPhone = contactPhone; }
    public String getPickupStreet() { return pickupStreet; }
    public void setPickupStreet(String pickupStreet) { this.pickupStreet = pickupStreet; }
    public String getPickupCity() { return pickupCity; }
    public void setPickupCity(String pickupCity) { this.pickupCity = pickupCity; }
    public String getPickupDistrict() { return pickupDistrict; }
    public void setPickupDistrict(String pickupDistrict) { this.pickupDistrict = pickupDistrict; }
}
