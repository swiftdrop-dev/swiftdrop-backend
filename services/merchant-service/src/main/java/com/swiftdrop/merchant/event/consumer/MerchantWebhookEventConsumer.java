package com.swiftdrop.merchant.event.consumer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.merchant.config.RabbitMqConfig;
import com.swiftdrop.merchant.entity.Merchant;
import com.swiftdrop.merchant.entity.WebhookDelivery;
import com.swiftdrop.merchant.entity.WebhookEndpoint;
import com.swiftdrop.merchant.entity.enums.WebhookEventType;
import com.swiftdrop.merchant.repository.MerchantRepository;
import com.swiftdrop.merchant.repository.WebhookDeliveryRepository;
import com.swiftdrop.merchant.repository.WebhookEndpointRepository;
import com.swiftdrop.merchant.service.webhook.WebhookDispatcherService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Component
public class MerchantWebhookEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(MerchantWebhookEventConsumer.class);

    private final MerchantRepository merchantRepository;
    private final WebhookEndpointRepository endpointRepository;
    private final WebhookDeliveryRepository deliveryRepository;
    private final WebhookDispatcherService dispatcherService;
    private final ObjectMapper objectMapper;

    public MerchantWebhookEventConsumer(
            MerchantRepository merchantRepository,
            WebhookEndpointRepository endpointRepository,
            WebhookDeliveryRepository deliveryRepository,
            WebhookDispatcherService dispatcherService,
            ObjectMapper objectMapper) {
        this.merchantRepository = merchantRepository;
        this.endpointRepository = endpointRepository;
        this.deliveryRepository = deliveryRepository;
        this.dispatcherService = dispatcherService;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = RabbitMqConfig.WEBHOOK_QUEUE)
    @Transactional
    public void handleLifecycleEvent(Object rawMessage) {
        if (rawMessage == null) {
            log.warn("Received null message on merchant webhook queue, discarding");
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
            log.error("Failed to parse event on merchant webhook queue: {}", ex.getMessage());
            throw new AmqpRejectAndDontRequeueException("Malformed JSON message", ex);
        }

        String trackingNumber = getString(payload, "trackingNumber");
        String parcelId = getString(payload, "parcelId");
        if ((trackingNumber == null || trackingNumber.isBlank()) && (parcelId == null || parcelId.isBlank())) {
            log.warn("Event missing tracking identifiers: {}", payload);
            throw new AmqpRejectAndDontRequeueException("Missing tracking identifier");
        }

        if (trackingNumber == null || trackingNumber.isBlank()) {
            trackingNumber = parcelId;
        }
        if (parcelId == null || parcelId.isBlank()) {
            parcelId = trackingNumber;
        }

        // Match to a registered merchant
        Optional<Merchant> merchantOpt = resolveMerchant(payload);
        if (merchantOpt.isEmpty()) {
            log.debug("Event on parcel [{}] does not belong to any merchant; ignoring", trackingNumber);
            return;
        }

        Merchant merchant = merchantOpt.get();

        String rawEventType = getString(payload, "eventType");
        String normalizedEventType = resolveWebhookEventType(rawEventType, payload);
        if (normalizedEventType == null) {
            log.info("Ignoring unhandled event type [{}] for merchant [{}]", rawEventType, merchant.getId());
            return;
        }

        String occurredAt = getString(payload, "occurredAt");
        String eventId = getString(payload, "eventId");
        if (eventId == null || eventId.isBlank()) {
            eventId = UUID.nameUUIDFromBytes((normalizedEventType + ":" + trackingNumber + ":" + occurredAt).getBytes(StandardCharsets.UTF_8)).toString();
        }

        List<WebhookEndpoint> endpoints = endpointRepository.findByMerchantIdAndIsActiveTrueAndDeletedAtIsNull(merchant.getId());
        if (endpoints.isEmpty()) {
            log.debug("Merchant [{}] has no active webhook endpoints", merchant.getId());
            return;
        }

        // Build standardized payload for merchant webhooks
        Map<String, Object> webhookData = new LinkedHashMap<>();
        webhookData.put("eventId", eventId);
        webhookData.put("eventType", normalizedEventType);
        webhookData.put("merchantId", merchant.getId().toString());
        webhookData.put("trackingNumber", trackingNumber);
        webhookData.put("parcelId", parcelId);
        webhookData.put("timestamp", Instant.now().toString());
        webhookData.put("data", payload);

        String jsonPayload;
        try {
            jsonPayload = objectMapper.writeValueAsString(webhookData);
        } catch (Exception e) {
            log.error("Failed to serialize webhook payload: {}", e.getMessage());
            throw new IllegalStateException("Failed to serialize webhook payload", e);
        }

        for (WebhookEndpoint endpoint : endpoints) {
            if (!endpoint.subscribesTo(normalizedEventType)) {
                continue;
            }

            if (deliveryRepository.existsByEndpointIdAndEventId(endpoint.getId(), eventId)) {
                log.info("Webhook delivery for endpoint [{}] and eventId [{}] already exists, skipping",
                        endpoint.getId(), eventId);
                continue;
            }

            WebhookDelivery delivery = new WebhookDelivery(
                    endpoint,
                    merchant.getId(),
                    eventId,
                    normalizedEventType,
                    trackingNumber,
                    parcelId,
                    jsonPayload
            );

            try {
                WebhookDelivery saved = deliveryRepository.saveAndFlush(delivery);
                dispatcherService.executeDelivery(saved);
                log.info("Dispatched webhook delivery [{}] for endpoint [{}] on event [{}]",
                        saved.getId(), endpoint.getId(), normalizedEventType);
            } catch (DataIntegrityViolationException ex) {
                log.info("Duplicate delivery attempt prevented by unique constraint for endpoint [{}] and eventId [{}]",
                        endpoint.getId(), eventId);
            } catch (Exception ex) {
                log.error("Failed to process webhook delivery for endpoint [{}]: {}", endpoint.getId(), ex.getMessage());
            }
        }
    }

    private Optional<Merchant> resolveMerchant(Map<String, Object> payload) {
        String merchantIdStr = getString(payload, "merchantId");
        if (merchantIdStr != null && !merchantIdStr.isBlank()) {
            try {
                UUID merchantId = UUID.fromString(merchantIdStr.trim());
                Optional<Merchant> m = merchantRepository.findById(merchantId);
                if (m.isPresent()) {
                    return m;
                }
            } catch (IllegalArgumentException ignored) {
            }
        }

        String senderId = getString(payload, "senderId");
        if (senderId != null && !senderId.isBlank()) {
            try {
                UUID senderUuid = UUID.fromString(senderId.trim());
                Optional<Merchant> m = merchantRepository.findByUserId(senderUuid);
                if (m.isPresent()) {
                    return m;
                }
            } catch (IllegalArgumentException ignored) {
            }
        }

        return Optional.empty();
    }

    private String resolveWebhookEventType(String rawEventType, Map<String, Object> payload) {
        String eventType = rawEventType != null ? rawEventType.toUpperCase() : "";
        String status = getString(payload, "status");
        if (status != null) {
            status = status.toUpperCase();
        }

        if (eventType.contains("CREATED") || eventType.contains("BOOK") || "CREATED".equals(status)) {
            return WebhookEventType.PARCEL_CREATED.name();
        }
        if (eventType.contains("ASSIGNED") || eventType.contains("ACCEPTED") || "ASSIGNED".equals(status)) {
            return WebhookEventType.PARCEL_ASSIGNED.name();
        }
        if (eventType.contains("PICKED_UP") || eventType.contains("PICKEDUP") || "PICKED_UP".equals(status)) {
            return WebhookEventType.PARCEL_PICKED_UP.name();
        }
        if (eventType.contains("IN_TRANSIT") || "IN_TRANSIT".equals(status)) {
            return WebhookEventType.PARCEL_IN_TRANSIT.name();
        }
        if (eventType.contains("OUT_FOR_DELIVERY") || "OUT_FOR_DELIVERY".equals(status)) {
            return WebhookEventType.PARCEL_OUT_FOR_DELIVERY.name();
        }
        if (eventType.contains("DELIVERY_FAILED") || eventType.contains("FAILED") || "DELIVERY_FAILED".equals(status)) {
            return WebhookEventType.PARCEL_DELIVERY_FAILED.name();
        }
        if (eventType.contains("DELIVERED") || eventType.contains("COMPLETED") || "DELIVERED".equals(status)) {
            return WebhookEventType.PARCEL_DELIVERED.name();
        }

        return null;
    }

    private String getString(Map<String, Object> map, String key) {
        Object val = map.get(key);
        return val != null ? val.toString() : null;
    }
}
