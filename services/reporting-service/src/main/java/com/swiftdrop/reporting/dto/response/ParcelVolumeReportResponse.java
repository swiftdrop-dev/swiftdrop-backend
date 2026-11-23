package com.swiftdrop.reporting.dto.response;

import java.util.List;

public record ParcelVolumeReportResponse(
        String period,
        long totalVolume,
        List<DailyVolumeItem> dailyStats
) {}
