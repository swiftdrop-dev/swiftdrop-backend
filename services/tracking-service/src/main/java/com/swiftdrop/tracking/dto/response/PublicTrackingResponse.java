package com.swiftdrop.tracking.dto.response;

import java.time.Instant;
import java.util.List;

public record PublicTrackingResponse(
        String trackingNumber,
        String currentStatus,
        Instant latestUpdate,
        List<TrackingTimelineItemDto> timeline
) {
}
