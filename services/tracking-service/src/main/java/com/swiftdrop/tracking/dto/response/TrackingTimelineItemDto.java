package com.swiftdrop.tracking.dto.response;

import java.time.Instant;

public record TrackingTimelineItemDto(
        String status,
        String title,
        String description,
        Instant timestamp,
        String location
) {
}
