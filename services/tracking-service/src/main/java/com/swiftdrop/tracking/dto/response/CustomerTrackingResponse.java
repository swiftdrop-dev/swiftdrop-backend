package com.swiftdrop.tracking.dto.response;

import java.time.Instant;
import java.util.List;

public record CustomerTrackingResponse(
        String trackingNumber,
        String parcelId,
        String currentStatus,
        Instant latestUpdate,
        List<TrackingTimelineItemDto> timeline
) {
}
