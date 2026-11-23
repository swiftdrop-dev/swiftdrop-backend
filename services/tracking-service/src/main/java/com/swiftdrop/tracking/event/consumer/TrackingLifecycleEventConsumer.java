package com.swiftdrop.tracking.event.consumer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.tracking.config.RabbitMqConfig;
import com.swiftdrop.tracking.entity.TrackingEvent;
import com.swiftdrop.tracking.repository.TrackingEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class TrackingLifecycleEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(TrackingLifecycleEventConsumer.class);

    private final TrackingEventRepository repository;
    private final ObjectMapper objectMapper;

    public TrackingLifecycleEventConsumer(TrackingEventRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = RabbitMqConfig.TRACKING_QUEUE)
    @Transactional
    public void handleDeliveryLifecycleEvent(Object rawMessage) {
        if (rawMessage == null) {
            log.warn("Received null message on tracking queue, discarding");
            throw new AmqpRejectAndDontRequeueException("Payload cannot be null");
        }

        Map<String, Object> payload;
        try {
            if (rawMessage instanceof org.springframework.amqp.core.Message msg) {
                rawMessage = msg.getBody();
            }
            if (rawMessage instanceof byte[] bytes) {
                rawMessage = new String(bytes, StandardCharsets.UTF_8);
            }
            if (rawMessage instanceof String jsonStr) {
                jsonStr = jsonStr.trim();
                if (jsonStr.startsWith("\"") && jsonStr.endsWith("\"")) {
                    Object unescaped = objectMapper.readValue(jsonStr, Object.class);
                    if (unescaped instanceof String s) {
                        jsonStr = s.trim();
                    }
                }
                payload = objectMapper.readValue(jsonStr, new TypeReference<>() {});
            } else if (rawMessage instanceof Map<?, ?> map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> casted = (Map<String, Object>) map;
                payload = casted;
            } else {
                payload = objectMapper.convertValue(rawMessage, new TypeReference<>() {});
            }
        } catch (Exception ex) {
            log.error("Failed to parse event on tracking queue: {}", ex.getMessage());
            throw new AmqpRejectAndDontRequeueException("Malformed JSON message", ex);
        }

        String trackingNumber = getString(payload, "trackingNumber");
        String parcelId = getString(payload, "parcelId");
        if ((trackingNumber == null || trackingNumber.isBlank()) && (parcelId == null || parcelId.isBlank())) {
            log.warn("Event missing both trackingNumber and parcelId: {}", payload);
            throw new AmqpRejectAndDontRequeueException("Missing tracking identifier");
        }

        if (trackingNumber == null || trackingNumber.isBlank()) {
            trackingNumber = parcelId;
        }
        if (parcelId == null || parcelId.isBlank()) {
            parcelId = trackingNumber;
        }

        String eventType = getString(payload, "eventType");
        if (eventType == null || eventType.isBlank()) {
            eventType = "PARCEL_UPDATE";
        }

        String occurredAtStr = getString(payload, "occurredAt");
        Instant timestamp = parseInstant(occurredAtStr);

        String eventId = getString(payload, "eventId");
        if (eventId == null || eventId.isBlank()) {
            eventId = UUID.nameUUIDFromBytes((eventType + ":" + trackingNumber + ":" + timestamp.toString()).getBytes(StandardCharsets.UTF_8)).toString();
        }

        // Idempotency check
        if (repository.existsByEventId(eventId)) {
            log.info("Tracking event [eventId={}] already processed, skipping", eventId);
            return;
        }

        String status = resolveStatus(eventType, payload);
        if (status == null) {
            log.info("Ignoring non-lifecycle or unrecognized event [eventType={}]", eventType);
            return;
        }

        String customerId = extractCustomerId(payload, trackingNumber);
        String title = resolveTitle(status, eventType);
        String description = resolveDescription(status, payload);
        String location = extractLocation(payload);
        String courierId = getString(payload, "courierId");

        TrackingEvent event = new TrackingEvent(
                eventId,
                trackingNumber,
                parcelId,
                eventType,
                status,
                title,
                description,
                location,
                courierId,
                customerId,
                timestamp
        );

        try {
            repository.save(event);
            log.info("Persisted tracking event [tracking={}, status={}, eventId={}]", trackingNumber, status, eventId);
        } catch (DataIntegrityViolationException e) {
            log.info("Tracking event [eventId={}] concurrent duplicate prevented by constraint", eventId);
        }
    }

    private String extractCustomerId(Map<String, Object> payload, String trackingNumber) {
        String customerId = getString(payload, "senderId");
        if (customerId == null || customerId.isBlank()) {
            customerId = getString(payload, "customerId");
        }
        if (customerId == null || customerId.isBlank()) {
            customerId = getString(payload, "sender_id");
        }
        if (customerId == null || customerId.isBlank()) {
            customerId = getString(payload, "customer_id");
        }
        if (customerId == null || customerId.isBlank()) {
            List<TrackingEvent> prior = repository.findByTrackingNumberOrderByTimestampAsc(trackingNumber);
            for (TrackingEvent prev : prior) {
                if (prev.getCustomerId() != null && !prev.getCustomerId().isBlank()) {
                    return prev.getCustomerId();
                }
            }
        }
        return customerId;
    }

    private String resolveStatus(String eventType, Map<String, Object> payload) {
        String explicitStatus = getString(payload, "status");
        if (explicitStatus != null && !explicitStatus.isBlank()) {
            return normalizeStatus(explicitStatus);
        }
        String upperType = eventType != null ? eventType.toUpperCase() : "";
        if (upperType.contains("CREATED") || upperType.contains("BOOKING")) return "pending";
        if (upperType.contains("ASSIGNED") || upperType.contains("ACCEPTED")) return "assigned";
        if (upperType.contains("PICKED_UP") || upperType.contains("PICKEDUP")) return "picked_up";
        if (upperType.contains("OUT_FOR_DELIVERY")) return "out_for_delivery";
        if (upperType.contains("IN_TRANSIT")) return "in_transit";
        if (upperType.contains("DELIVERED") || upperType.contains("COMPLETED")) return "delivered";
        if (upperType.contains("FAILED") || upperType.contains("DECLINED")) return "failed";
        if (upperType.contains("CANCELLED")) return "cancelled";
        if (upperType.contains("RETURNED")) return "returned";
        return null;
    }

    private String normalizeStatus(String status) {
        String s = status.toLowerCase();
        if (s.equals("created") || s.equals("pending_dispatch")) return "pending";
        if (s.equals("assigned_pending_acceptance") || s.equals("accepted")) return "assigned";
        return s;
    }

    private String resolveTitle(String status, String eventType) {
        return switch (status) {
            case "pending" -> "Booking Confirmed";
            case "assigned" -> "Courier Assigned";
            case "picked_up" -> "Package Picked Up";
            case "in_transit" -> "In Transit";
            case "out_for_delivery" -> "Out for Delivery";
            case "delivered" -> "Delivered";
            case "failed" -> "Delivery Attempt Failed";
            case "cancelled" -> "Cancelled";
            case "returned" -> "Returned to Sender";
            default -> "Status Update: " + status;
        };
    }

    private String resolveDescription(String status, Map<String, Object> payload) {
        String failureReason = getString(payload, "failureReason");
        if (failureReason == null || failureReason.isBlank()) {
            failureReason = getString(payload, "reason");
        }
        if (failureReason != null && !failureReason.isBlank()) {
            return failureReason;
        }

        String notes = getString(payload, "notes");
        if (notes != null && !notes.isBlank()) {
            return notes;
        }

        return switch (status) {
            case "pending" -> "Parcel booking has been registered in the system.";
            case "assigned" -> "A courier has been assigned to transport your parcel.";
            case "picked_up" -> "Courier has collected the package from pickup location.";
            case "in_transit" -> "Package is moving through the SwiftDrop delivery network.";
            case "out_for_delivery" -> "Courier is en route to the delivery destination.";
            case "delivered" -> "Package delivered successfully.";
            case "failed" -> "Delivery attempt was unsuccessful.";
            case "cancelled" -> "Delivery was cancelled.";
            default -> "Delivery progress updated.";
        };
    }

    private String extractLocation(Map<String, Object> payload) {
        String location = getString(payload, "location");
        if (location != null && !location.isBlank()) {
            return location;
        }
        Object pickup = payload.get("pickupAddress");
        if (pickup instanceof Map<?, ?> pickupMap) {
            Object city = pickupMap.get("city");
            if (city != null) return city.toString();
        }
        Object delivery = payload.get("deliveryAddress");
        if (delivery instanceof Map<?, ?> deliveryMap) {
            Object city = deliveryMap.get("city");
            if (city != null) return city.toString();
        }
        return null;
    }

    private String getString(Map<String, Object> map, String key) {
        Object val = map.get(key);
        return val != null ? val.toString() : null;
    }

    private Instant parseInstant(String raw) {
        if (raw == null || raw.isBlank()) {
            return Instant.now();
        }
        try {
            return Instant.parse(raw);
        } catch (Exception e) {
            return Instant.now();
        }
    }
}
