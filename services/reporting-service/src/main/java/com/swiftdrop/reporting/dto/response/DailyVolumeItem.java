package com.swiftdrop.reporting.dto.response;

public record DailyVolumeItem(
        String date,
        long total,
        long delivered,
        long failed,
        double revenue
) {}
