package com.swiftdrop.merchant.dto.response;

import com.swiftdrop.merchant.entity.enums.BusinessType;
import com.swiftdrop.merchant.entity.enums.MerchantStatus;
import com.swiftdrop.merchant.entity.enums.PricingTier;

import java.time.Instant;
import java.util.UUID;

public class MerchantSummaryResponse {

    private UUID id;
    private UUID userId;
    private String businessName;
    private String tradeName;
    private String registrationNumber;
    private BusinessType businessType;
    private MerchantStatus status;
    private PricingTier pricingTier;
    private Instant createdAt;

    public MerchantSummaryResponse() {
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
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

    public BusinessType getBusinessType() {
        return businessType;
    }

    public void setBusinessType(BusinessType businessType) {
        this.businessType = businessType;
    }

    public MerchantStatus getStatus() {
        return status;
    }

    public void setStatus(MerchantStatus status) {
        this.status = status;
    }

    public PricingTier getPricingTier() {
        return pricingTier;
    }

    public void setPricingTier(PricingTier pricingTier) {
        this.pricingTier = pricingTier;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
