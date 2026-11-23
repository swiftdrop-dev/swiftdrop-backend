package com.swiftdrop.reporting.dto.response;

import java.util.List;

public record MerchantVolumeResponse(
        long totalMerchants,
        long pendingApplications,
        List<MerchantVolumeItem> merchants
) {}
