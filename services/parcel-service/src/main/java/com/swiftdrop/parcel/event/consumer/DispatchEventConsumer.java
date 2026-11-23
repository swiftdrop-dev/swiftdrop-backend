package com.swiftdrop.parcel.event.consumer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.parcel.config.RabbitMqConfig;
import com.swiftdrop.parcel.entity.Parcel;
import com.swiftdrop.parcel.event.ParcelBookedApplicationEvent;
import com.swiftdrop.parcel.repository.ParcelRepository;
import com.swiftdrop.parcel.service.OutboxEventService;
import com.swiftdrop.parcel.service.ParcelAssignmentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class DispatchEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(DispatchEventConsumer.class);

    private final ParcelAssignmentService parcelAssignmentService;
    private final ParcelRepository parcelRepository;
    private final OutboxEventService outboxEventService;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher applicationEventPublisher;

    public DispatchEventConsumer(ParcelAssignmentService parcelAssignmentService,
                                 ParcelRepository parcelRepository,
                                 OutboxEventService outboxEventService,
                                 ObjectMapper objectMapper,
                                 ApplicationEventPublisher applicationEventPublisher) {
        this.parcelAssignmentService = parcelAssignmentService;
        this.parcelRepository = parcelRepository;
        this.outboxEventService = outboxEventService;
        this.objectMapper = objectMapper;
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @RabbitListener(queues = RabbitMqConfig.DISPATCH_EVENTS_QUEUE)
    @Transactional
    public void handleDispatchEvent(Object rawMessage) {
        if (rawMessage == null) {
            log.warn("Received null message on dispatch events queue, discarding");
            throw new AmqpRejectAndDontRequeueException("Payload cannot be null");
        }

        Map<String, Object> payload;
        try {
            Object parsed;
            if (rawMessage instanceof Message amqpMessage) {
                String str = new String(amqpMessage.getBody(), StandardCharsets.UTF_8);
                parsed = objectMapper.readValue(str, Object.class);
            } else if (rawMessage instanceof String jsonStr) {
                parsed = objectMapper.readValue(jsonStr, Object.class);
            } else if (rawMessage instanceof byte[] bytes) {
                parsed = objectMapper.readValue(new String(bytes, StandardCharsets.UTF_8), Object.class);
            } else {
                parsed = rawMessage;
            }

            if (parsed instanceof String innerStr) {
                payload = objectMapper.readValue(innerStr, new TypeReference<>() {});
            } else if (parsed instanceof Map<?, ?> map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> casted = (Map<String, Object>) map;
                payload = casted;
            } else {
                payload = objectMapper.convertValue(parsed, new TypeReference<>() {});
            }
        } catch (Exception ex) {
            log.error("Failed to parse event on dispatch events queue: {}", ex.getMessage());
            throw new AmqpRejectAndDontRequeueException("Malformed JSON message", ex);
        }

        String rawParcelId = getString(payload, "parcelId");
        String courierId = getString(payload, "courierId");
        String trackingNumber = getString(payload, "trackingNumber");

        if (courierId == null || courierId.isBlank()) {
            log.warn("Dispatch event missing courierId: {}", payload);
            return;
        }

        Parcel parcel = null;
        if (rawParcelId != null && !rawParcelId.isBlank()) {
            parcel = parcelRepository.findById(rawParcelId).orElse(null);
        }
        if (parcel == null && trackingNumber != null && !trackingNumber.isBlank()) {
            parcel = parcelRepository.findByTrackingNumber(trackingNumber).orElse(null);
        }

        String parcelId = (parcel != null) ? parcel.getId() : rawParcelId;
        if (parcelId == null || parcelId.isBlank()) {
            log.warn("Dispatch event missing parcel identifier: {}", payload);
            return;
        }

        log.info("Processing dispatch assignment for parcelId={}, courierId={}", parcelId, courierId);

        try {
            parcelAssignmentService.assign(parcelId, courierId, "DISPATCH_SERVICE", "Courier accepted dispatch assignment");
        } catch (Exception ex) {
            log.warn("Error during parcel assignment for parcelId={}: {}", parcelId, ex.getMessage());
            return;
        }

        if (parcel != null) {
            String currentStatus = parcel.getStatus();
            if (currentStatus == null || "CREATED".equalsIgnoreCase(currentStatus) || "pending".equalsIgnoreCase(currentStatus)) {
                parcel.changeStatus("assigned");
                parcelRepository.saveAndFlush(parcel);

                if (outboxEventService != null && objectMapper != null) {
                    Map<String, Object> event = new HashMap<>();
                    event.put("eventId", UUID.randomUUID().toString());
                    event.put("eventType", "PARCEL_ASSIGNED");
                    event.put("eventVersion", "1.0");
                    event.put("occurredAt", Instant.now().toString());
                    event.put("parcelId", parcel.getId());
                    event.put("trackingNumber", parcel.getTrackingNumber() != null ? parcel.getTrackingNumber() : trackingNumber);
                    event.put("status", "assigned");
                    event.put("courierId", courierId);
                    event.put("senderId", parcel.getSenderId());
                    event.put("customerId", parcel.getSenderId());
                    event.put("description", "Parcel assigned to courier");
                    try {
                        String payloadJson = objectMapper.writeValueAsString(event);
                        outboxEventService.saveEvent("PARCEL", parcel.getId(), "PARCEL_ASSIGNED", payloadJson);
                        if (applicationEventPublisher != null) {
                            applicationEventPublisher.publishEvent(new ParcelBookedApplicationEvent(parcel.getId()));
                        }
                    } catch (Exception ex) {
                        log.error("Failed to persist PARCEL_ASSIGNED outbox event", ex);
                    }
                }
            }
        }
    }

    private String getString(Map<String, Object> map, String key) {
        Object val = map.get(key);
        return val != null ? String.valueOf(val).trim() : null;
    }
}
