package com.swiftdrop.merchant.dto.request;

import com.swiftdrop.merchant.entity.enums.BusinessType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class RegisterMerchantRequest {

    @NotBlank(message = "Business name is required")
    @Size(max = 255, message = "Business name cannot exceed 255 characters")
    private String businessName;

    @Size(max = 255, message = "Trade name cannot exceed 255 characters")
    private String tradeName;

    @NotBlank(message = "Registration number is required")
    @Size(max = 100, message = "Registration number cannot exceed 100 characters")
    private String registrationNumber;

    @Size(max = 100, message = "Tax ID cannot exceed 100 characters")
    private String taxId;

    @NotNull(message = "Business type is required")
    private BusinessType businessType;

    @NotBlank(message = "Primary contact name is required")
    private String contactName;

    @NotBlank(message = "Primary contact phone is required")
    private String contactPhone;

    @NotBlank(message = "Primary contact email is required")
    @Email(message = "Valid email is required")
    private String contactEmail;

    public RegisterMerchantRequest() {
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

    public String getRegistrationNumber() {
        return registrationNumber;
    }

    public void setRegistrationNumber(String registrationNumber) {
        this.registrationNumber = registrationNumber;
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

    public String getContactName() {
        return contactName;
    }

    public void setContactName(String contactName) {
        this.contactName = contactName;
    }

    public String getContactPhone() {
        return contactPhone;
    }

    public void setContactPhone(String contactPhone) {
        this.contactPhone = contactPhone;
    }

    public String getContactEmail() {
        return contactEmail;
    }

    public void setContactEmail(String contactEmail) {
        this.contactEmail = contactEmail;
    }
}
