package com.swiftdrop.reporting.dto.response;

public record MerchantVolumeItem(
        String merchantId,
        String businessName,
        String pricingTier,
        String status,
        long parcelCount
) {}
