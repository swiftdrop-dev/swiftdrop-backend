package com.swiftdrop.reporting.dto.response;

public record StatusDistributionItem(
        String status,
        long count,
        double percentage
) {}
