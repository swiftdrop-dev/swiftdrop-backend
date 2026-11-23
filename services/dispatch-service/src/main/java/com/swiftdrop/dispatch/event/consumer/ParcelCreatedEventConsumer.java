package com.swiftdrop.dispatch.event.consumer;

import com.swiftdrop.dispatch.config.RabbitMqConfig;
import com.swiftdrop.dispatch.dto.request.CreateDispatchJobRequest;
import com.swiftdrop.dispatch.entity.enums.DispatchPriority;
import com.swiftdrop.dispatch.entity.enums.VehicleType;
import com.swiftdrop.dispatch.event.ParcelCreatedEvent;
import com.swiftdrop.dispatch.exception.DuplicateResourceException;
import com.swiftdrop.dispatch.repository.DispatchJobRepository;
import com.swiftdrop.dispatch.service.DispatchJobService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Component
public class ParcelCreatedEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(ParcelCreatedEventConsumer.class);

    private static final BigDecimal DEFAULT_PICKUP_LAT = new BigDecimal("6.9271");
    private static final BigDecimal DEFAULT_PICKUP_LNG = new BigDecimal("79.8612");
    private static final BigDecimal DEFAULT_DELIVERY_LAT = new BigDecimal("6.9034");
    private static final BigDecimal DEFAULT_DELIVERY_LNG = new BigDecimal("79.8550");

    private final DispatchJobService dispatchJobService;
    private final DispatchJobRepository jobRepository;

    public ParcelCreatedEventConsumer(DispatchJobService dispatchJobService, DispatchJobRepository jobRepository) {
        this.dispatchJobService = dispatchJobService;
        this.jobRepository = jobRepository;
    }

    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules();

    @RabbitListener(queues = RabbitMqConfig.PARCEL_CREATED_QUEUE)
    public void handleParcelCreated(Object rawMessage) {
        if (rawMessage == null) {
            log.error("Received null ParcelCreatedEvent payload; routing to DLQ.");
            throw new AmqpRejectAndDontRequeueException("Null event payload");
        }

        ParcelCreatedEvent event;
        if (rawMessage instanceof ParcelCreatedEvent pce) {
            event = pce;
        } else {
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
                    event = objectMapper.readValue(jsonStr, ParcelCreatedEvent.class);
                } else {
                    event = objectMapper.convertValue(rawMessage, ParcelCreatedEvent.class);
                }
            } catch (Exception ex) {
                log.error("Failed to parse ParcelCreatedEvent from message: {}", ex.getMessage());
                throw new AmqpRejectAndDontRequeueException("Malformed event payload", ex);
            }
        }

        log.info("Received parcel created event [eventId={}, parcelId={}, trackingNumber={}]",
                event.getEventId(), event.getParcelId(), event.getTrackingNumber());

        // 1. Validate required fields
        if (!isValid(event)) {
            log.error("Malformed ParcelCreatedEvent [eventId={}, parcelId={}]; rejecting without requeue to DLQ.",
                    event.getEventId(), event.getParcelId());
            throw new AmqpRejectAndDontRequeueException("Invalid ParcelCreatedEvent payload: missing required fields");
        }

        // 2. Resolve parcel and sender UUIDs safely
        UUID parcelUuid = resolveUuid(event.getParcelId());
        UUID senderUuid = resolveUuid(event.getSenderId() != null ? event.getSenderId() : "cust_anonymous");

        // 3. Idempotency Check (pre-insert)
        if (jobRepository.existsByParcelId(parcelUuid) ||
                jobRepository.findByTrackingNumber(event.getTrackingNumber()).isPresent()) {
            log.info("Dispatch job already exists for parcelId={} / trackingNumber={}. Idempotently skipping.",
                    parcelUuid, event.getTrackingNumber());
            return;
        }

        // 4. Map to CreateDispatchJobRequest and invoke service
        try {
            CreateDispatchJobRequest request = mapToRequest(event, parcelUuid, senderUuid);
            dispatchJobService.createJob(request);
            log.info("Successfully created unassigned dispatch job for parcelId={}, trackingNumber={}",
                    parcelUuid, event.getTrackingNumber());
        } catch (DuplicateResourceException e) {
            log.info("Duplicate dispatch job detected during creation for parcelId={}: {}. Idempotently handled.",
                    parcelUuid, e.getMessage());
        } catch (DataIntegrityViolationException e) {
            log.info("Unique constraint violation for parcelId={} (concurrent consumer): {}. Idempotently handled.",
                    parcelUuid, e.getMessage());
        } catch (Exception e) {
            log.error("Retryable error processing parcel created event for parcelId={}: {}",
                    parcelUuid, e.getMessage(), e);
            throw e; // Triggers RabbitMQ retry policy
        }
    }

    private boolean isValid(ParcelCreatedEvent event) {
        return event.getParcelId() != null && !event.getParcelId().isBlank()
                && event.getTrackingNumber() != null && !event.getTrackingNumber().isBlank()
                && event.getPickupAddress() != null && !event.getPickupAddress().isBlank()
                && event.getDeliveryAddress() != null && !event.getDeliveryAddress().isBlank()
                && event.getWeightKg() != null && event.getWeightKg().compareTo(BigDecimal.ZERO) > 0;
    }

    private UUID resolveUuid(String id) {
        if (id == null || id.isBlank()) {
            return UUID.randomUUID();
        }
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            return UUID.nameUUIDFromBytes(id.getBytes(StandardCharsets.UTF_8));
        }
    }

    private CreateDispatchJobRequest mapToRequest(ParcelCreatedEvent event, UUID parcelUuid, UUID senderUuid) {
        CreateDispatchJobRequest request = new CreateDispatchJobRequest();
        request.setParcelId(parcelUuid);
        request.setTrackingNumber(event.getTrackingNumber());
        request.setMerchantId(senderUuid);
        request.setZoneCode(event.getZoneCode());

        request.setPickupStreetAddress(event.getPickupAddress());
        request.setPickupCity(event.getPickupCity() != null && !event.getPickupCity().isBlank()
                ? event.getPickupCity() : extractCity(event.getPickupAddress()));
        request.setPickupLatitude(event.getPickupLatitude() != null
                ? event.getPickupLatitude() : DEFAULT_PICKUP_LAT);
        request.setPickupLongitude(event.getPickupLongitude() != null
                ? event.getPickupLongitude() : DEFAULT_PICKUP_LNG);

        request.setDeliveryStreetAddress(event.getDeliveryAddress());
        request.setDeliveryCity(event.getDeliveryCity() != null && !event.getDeliveryCity().isBlank()
                ? event.getDeliveryCity() : extractCity(event.getDeliveryAddress()));
        request.setDeliveryLatitude(event.getDeliveryLatitude() != null
                ? event.getDeliveryLatitude() : DEFAULT_DELIVERY_LAT);
        request.setDeliveryLongitude(event.getDeliveryLongitude() != null
                ? event.getDeliveryLongitude() : DEFAULT_DELIVERY_LNG);

        request.setParcelWeightKg(event.getWeightKg());

        // Vehicle Type Resolution
        if (event.getWeightKg().compareTo(new BigDecimal("20.0")) > 0
                || "BULK".equalsIgnoreCase(event.getParcelSize())
                || "HEAVY_BULK".equalsIgnoreCase(event.getParcelSize())) {
            request.setRequiredVehicleType(VehicleType.VAN);
        } else if (event.getWeightKg().compareTo(new BigDecimal("5.0")) > 0) {
            request.setRequiredVehicleType(VehicleType.CAR);
        } else {
            request.setRequiredVehicleType(VehicleType.MOTORCYCLE);
        }

        // Priority Resolution
        if (Boolean.TRUE.equals(event.getFragile())
                || "EXPRESS".equalsIgnoreCase(event.getParcelSize())
                || "HIGH".equalsIgnoreCase(event.getPriority())) {
            request.setPriority(DispatchPriority.HIGH);
        } else if ("URGENT".equalsIgnoreCase(event.getPriority())) {
            request.setPriority(DispatchPriority.URGENT);
        } else {
            request.setPriority(DispatchPriority.STANDARD);
        }

        return request;
    }

    private String extractCity(String address) {
        if (address == null || address.isBlank()) {
            return "Colombo";
        }
        String[] parts = address.split(",");
        if (parts.length > 1) {
            String candidate = parts[parts.length - 1].trim().replaceAll("\\b\\d+\\b", "").trim();
            if (!candidate.isBlank()) {
                return candidate.length() > 100 ? candidate.substring(0, 100) : candidate;
            }
        }
        return address.length() > 100 ? address.substring(0, 100) : address;
    }
}
