package com.swiftdrop.reporting.dto.response;

import java.util.List;

public record StatusDistributionResponse(
        long total,
        List<StatusDistributionItem> distribution
) {}
