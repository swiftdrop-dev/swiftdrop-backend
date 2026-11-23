package com.swiftdrop.tracking.service;

import com.swiftdrop.tracking.client.ParcelServiceClient;
import com.swiftdrop.tracking.client.dto.ParcelResponseDto;
import com.swiftdrop.tracking.dto.response.CustomerTrackingResponse;
import com.swiftdrop.tracking.dto.response.PublicTrackingResponse;
import com.swiftdrop.tracking.dto.response.TrackingTimelineItemDto;
import com.swiftdrop.tracking.entity.TrackingEvent;
import com.swiftdrop.tracking.exception.AccessDeniedException;
import com.swiftdrop.tracking.exception.ResourceNotFoundException;
import com.swiftdrop.tracking.exception.UnauthorizedException;
import com.swiftdrop.tracking.repository.TrackingEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class TrackingQueryService {

    private static final Logger log = LoggerFactory.getLogger(TrackingQueryService.class);

    private final TrackingEventRepository trackingEventRepository;
    private final ParcelServiceClient parcelServiceClient;

    public TrackingQueryService(TrackingEventRepository trackingEventRepository) {
        this(trackingEventRepository, null);
    }

    @Autowired
    public TrackingQueryService(TrackingEventRepository trackingEventRepository,
                                ParcelServiceClient parcelServiceClient) {
        this.trackingEventRepository = trackingEventRepository;
        this.parcelServiceClient = parcelServiceClient;
    }

    @Transactional(readOnly = true)
    public PublicTrackingResponse getPublicTracking(String trackingNumber) {
        List<TrackingEvent> events = trackingEventRepository.findByTrackingNumberOrderByTimestampAscCreatedAtAsc(trackingNumber);
        if (events == null || events.isEmpty()) {
            throw new ResourceNotFoundException("No tracking records found for tracking number: " + trackingNumber);
        }

        TrackingEvent first = events.get(0);
        EffectiveState effective = computeEffectiveState(events);

        List<TrackingTimelineItemDto> timeline = buildTimeline(events);

        return new PublicTrackingResponse(
                first.getTrackingNumber(),
                effective.status(),
                effective.timestamp(),
                timeline
        );
    }

    @Transactional(readOnly = true)
    public CustomerTrackingResponse getCustomerTracking(String trackingNumber, String userId, String userRoles) {
        if (userId == null || userId.isBlank()) {
            throw new UnauthorizedException("Authentication required to access detailed tracking");
        }

        List<TrackingEvent> events = trackingEventRepository.findByTrackingNumberOrderByTimestampAscCreatedAtAsc(trackingNumber);
        if (events == null || events.isEmpty()) {
            throw new ResourceNotFoundException("No tracking records found for tracking number: " + trackingNumber);
        }

        verifyOwnership(trackingNumber, userId, userRoles, events);

        TrackingEvent first = events.get(0);
        EffectiveState effective = computeEffectiveState(events);

        List<TrackingTimelineItemDto> timeline = buildTimeline(events);

        return new CustomerTrackingResponse(
                first.getTrackingNumber(),
                first.getParcelId(),
                effective.status(),
                effective.timestamp(),
                timeline
        );
    }

    public void verifyOwnership(String trackingNumber, String userId, String userRoles, List<TrackingEvent> events) {
        boolean isPrivileged = userRoles != null && (
                userRoles.toLowerCase().contains("admin") ||
                userRoles.toLowerCase().contains("dispatcher") ||
                userRoles.toLowerCase().contains("staff")
        );
        if (isPrivileged) {
            return;
        }

        String ownerId = null;
        for (TrackingEvent event : events) {
            if (event.getCustomerId() != null && !event.getCustomerId().isBlank()) {
                ownerId = event.getCustomerId();
                break;
            }
        }

        if (ownerId == null && parcelServiceClient != null) {
            ParcelResponseDto parcel = parcelServiceClient.getParcelByTrackingNumber(trackingNumber);
            if (parcel != null && parcel.getSenderId() != null) {
                ownerId = parcel.getSenderId();
            }
        }

        if (ownerId == null || !ownerId.trim().equalsIgnoreCase(userId.trim())) {
            log.warn("Access denied: user [{}] is not authorized to view tracking for parcel owned by [{}]", userId, ownerId);
            throw new AccessDeniedException("Access denied: You do not have permission to view detailed tracking for this parcel");
        }
    }

    public record EffectiveState(String status, Instant timestamp) {}

    private static final java.util.Set<String> TERMINAL_STATUSES = java.util.Set.of("delivered", "cancelled", "returned");

    private static final java.util.Map<String, java.util.Set<String>> ALLOWED_FORWARD_TRANSITIONS = java.util.Map.of(
            "pending", java.util.Set.of("assigned", "picked_up", "in_transit", "out_for_delivery", "delivery_failed", "failed", "cancelled"),
            "assigned", java.util.Set.of("picked_up", "in_transit", "out_for_delivery", "delivery_failed", "failed", "cancelled"),
            "picked_up", java.util.Set.of("in_transit", "out_for_delivery", "delivery_failed", "failed", "cancelled"),
            "in_transit", java.util.Set.of("out_for_delivery", "delivery_failed", "failed", "returned", "cancelled"),
            "out_for_delivery", java.util.Set.of("delivery_failed", "failed", "in_transit", "returned", "cancelled"),
            "delivery_failed", java.util.Set.of("out_for_delivery", "in_transit", "returned", "cancelled"),
            "failed", java.util.Set.of("out_for_delivery", "in_transit", "returned", "cancelled")
    );

    public EffectiveState computeEffectiveState(List<TrackingEvent> events) {
        if (events == null || events.isEmpty()) {
            return new EffectiveState("pending", Instant.now());
        }

        // Sort events chronologically by business event timestamp, tie-breaking by arrival createdAt
        List<TrackingEvent> sorted = events.stream()
                .sorted(java.util.Comparator.comparing(TrackingEvent::getTimestamp, java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder()))
                        .thenComparing(TrackingEvent::getCreatedAt, java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())))
                .toList();

        String effectiveStatus = "pending";
        Instant effectiveTimestamp = sorted.get(0).getTimestamp();

        for (TrackingEvent event : sorted) {
            String nextStatus = normalizeStatus(event.getStatus());

            // 1. Terminal states (DELIVERED, CANCELLED, RETURNED) are distinct and once reached cannot be replaced or regressed
            if (isTerminalStatus(effectiveStatus)) {
                continue;
            }

            // 2. Any active delivery state can transition to a terminal state
            if (isTerminalStatus(nextStatus)) {
                effectiveStatus = nextStatus;
                effectiveTimestamp = event.getTimestamp();
                continue;
            }

            // 3. Duplicate event with same status does not regress state
            if (nextStatus.equals(effectiveStatus)) {
                if (event.getTimestamp() != null && (effectiveTimestamp == null || event.getTimestamp().isAfter(effectiveTimestamp))) {
                    effectiveTimestamp = event.getTimestamp();
                }
                continue;
            }

            // 4. Non-linear lifecycle transitions (including delivery_failed -> retry via out_for_delivery / in_transit)
            if (isValidLifecycleTransition(effectiveStatus, nextStatus)) {
                effectiveStatus = nextStatus;
                effectiveTimestamp = event.getTimestamp();
            }
            // 5. Delayed out-of-order events from prior lifecycle phases are ignored for effective status
        }

        return new EffectiveState(effectiveStatus, effectiveTimestamp);
    }

    List<TrackingTimelineItemDto> buildTimeline(List<TrackingEvent> events) {
        if (events == null || events.isEmpty()) {
            return List.of();
        }
        List<TrackingTimelineItemDto> items = new java.util.ArrayList<>();
        TrackingTimelineItemDto last = null;

        for (TrackingEvent e : events) {
            TrackingTimelineItemDto item = new TrackingTimelineItemDto(
                    e.getStatus(),
                    e.getTitle(),
                    e.getDescription(),
                    e.getTimestamp(),
                    e.getLocation()
            );

            if (last != null && isDuplicateMilestone(last, item)) {
                continue;
            }

            items.add(item);
            last = item;
        }
        return items;
    }

    private boolean isDuplicateMilestone(TrackingTimelineItemDto prev, TrackingTimelineItemDto curr) {
        if (prev == null || curr == null) return false;
        boolean sameStatus = java.util.Objects.equals(prev.status(), curr.status());
        if (!sameStatus) {
            return false;
        }
        if (java.util.Objects.equals(prev.timestamp(), curr.timestamp())) {
            return true;
        }
        return java.util.Objects.equals(prev.title(), curr.title())
                && java.util.Objects.equals(prev.description(), curr.description())
                && java.util.Objects.equals(prev.location(), curr.location());
    }

    private boolean isValidLifecycleTransition(String current, String next) {
        if (current == null || next == null) return false;
        java.util.Set<String> allowed = ALLOWED_FORWARD_TRANSITIONS.get(current.toLowerCase());
        return allowed != null && allowed.contains(next.toLowerCase());
    }

    private boolean isTerminalStatus(String status) {
        if (status == null) return false;
        return TERMINAL_STATUSES.contains(status.toLowerCase());
    }

    public static String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return "pending";
        }
        String s = status.trim().toLowerCase();
        return switch (s) {
            case "created", "pending_dispatch" -> "pending";
            case "assigned_pending_acceptance", "accepted" -> "assigned";
            case "failed" -> "delivery_failed";
            default -> s;
        };
    }
}
