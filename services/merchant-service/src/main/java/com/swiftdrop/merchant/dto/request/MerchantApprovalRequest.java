package com.swiftdrop.merchant.dto.request;

import com.swiftdrop.merchant.entity.enums.PricingTier;

public class MerchantApprovalRequest {

    private PricingTier pricingTier = PricingTier.STANDARD;
    private String rejectionReason;

    public MerchantApprovalRequest() {
    }

    public PricingTier getPricingTier() {
        return pricingTier;
    }

    public void setPricingTier(PricingTier pricingTier) {
        this.pricingTier = pricingTier;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public void setRejectionReason(String rejectionReason) {
        this.rejectionReason = rejectionReason;
    }
}
