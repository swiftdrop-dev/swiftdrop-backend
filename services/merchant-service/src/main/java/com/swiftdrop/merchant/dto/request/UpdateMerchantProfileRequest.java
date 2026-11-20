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
}
