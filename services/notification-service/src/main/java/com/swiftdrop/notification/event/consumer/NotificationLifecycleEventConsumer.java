package com.swiftdrop.notification.event.consumer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.notification.config.RabbitMqConfig;
import com.swiftdrop.notification.entity.Notification;
import com.swiftdrop.notification.entity.enums.DeliveryStatus;
import com.swiftdrop.notification.entity.enums.NotificationChannel;
import com.swiftdrop.notification.entity.enums.NotificationType;
import com.swiftdrop.notification.provider.NotificationDispatcher;
import com.swiftdrop.notification.repository.NotificationRepository;
import com.swiftdrop.notification.service.template.NotificationTemplateContent;
import com.swiftdrop.notification.service.template.NotificationTemplateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

@Component
public class NotificationLifecycleEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(NotificationLifecycleEventConsumer.class);

    private final NotificationRepository repository;
    private final NotificationTemplateService templateService;
    private final NotificationDispatcher dispatcher;
    private final ObjectMapper objectMapper;

    public NotificationLifecycleEventConsumer(NotificationRepository repository,
                                              NotificationTemplateService templateService,
                                              NotificationDispatcher dispatcher,
                                              ObjectMapper objectMapper) {
        this.repository = repository;
        this.templateService = templateService;
        this.dispatcher = dispatcher;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = RabbitMqConfig.NOTIFICATION_QUEUE)
    @Transactional
    public void handleLifecycleEvent(Object rawMessage) {
        if (rawMessage == null) {
            log.warn("Received null message on notification queue, discarding");
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
            log.error("Failed to parse event on notification queue: {}", ex.getMessage());
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

        String rawEventType = getString(payload, "eventType");
        NotificationType notificationType = resolveNotificationType(rawEventType, payload);
        if (notificationType == null) {
            log.info("Ignoring event with non-supported notification type [eventType={}]", rawEventType);
            return;
        }

        String occurredAt = getString(payload, "occurredAt");
        String eventId = getString(payload, "eventId");
        if (eventId == null || eventId.isBlank()) {
            eventId = UUID.nameUUIDFromBytes((rawEventType + ":" + trackingNumber + ":" + occurredAt).getBytes(StandardCharsets.UTF_8)).toString();
        }

        // Idempotency check: duplicate event protection
        if (repository.existsByEventId(eventId)) {
            log.info("Notification event [eventId={}] already processed, skipping duplicate delivery", eventId);
            return;
        }

        String recipientId = resolveRecipientId(payload);
        String recipientEmail = getString(payload, "senderEmail");
        if (recipientEmail == null) recipientEmail = getString(payload, "recipientEmail");
        String recipientPhone = getString(payload, "senderPhone");
        if (recipientPhone == null) recipientPhone = getString(payload, "recipientPhone");

        NotificationTemplateContent content = templateService.generateContent(notificationType, trackingNumber, payload);

        Notification notification = new Notification(
                eventId,
                parcelId,
                trackingNumber,
                recipientId,
                recipientEmail,
                recipientPhone,
                NotificationChannel.EMAIL,
                notificationType,
                content.subject(),
                content.message(),
                DeliveryStatus.PENDING
        );

        try {
            Notification saved = repository.saveAndFlush(notification);
            dispatcher.dispatch(saved);
            repository.save(saved);
            log.info("Successfully processed notification [{}] for recipient [{}] on parcel [{}]",
                    saved.getId(), recipientId, trackingNumber);
        } catch (DataIntegrityViolationException e) {
            log.info("Concurrent duplicate notification prevented by unique constraint for eventId [{}]", eventId);
        }
    }

    private NotificationType resolveNotificationType(String eventType, Map<String, Object> payload) {
        String status = getString(payload, "status");
        String type = eventType != null ? eventType.toUpperCase() : "";

        if (type.contains("CREATED") || type.contains("BOOKING")) {
            return NotificationType.BOOKING_CONFIRMED;
        }
        if (type.contains("ASSIGNED") || type.contains("ACCEPTED")) {
            return NotificationType.COURIER_ASSIGNED;
        }
        if (type.contains("PICKED_UP") || type.contains("PICKEDUP")) {
            return NotificationType.PARCEL_PICKED_UP;
        }
        if (type.contains("IN_TRANSIT")) {
            return NotificationType.IN_TRANSIT;
        }
        if (type.contains("OUT_FOR_DELIVERY")) {
            return NotificationType.OUT_FOR_DELIVERY;
        }
        if (type.contains("DELIVERY_FAILED") || type.contains("FAILED")) {
            return NotificationType.DELIVERY_FAILED;
        }
        if (type.contains("DELIVERED") || type.contains("COMPLETED")) {
            return NotificationType.DELIVERY_SUCCESSFUL;
        }

        if (status != null && !status.isBlank()) {
            String s = status.toLowerCase();
            return switch (s) {
                case "created", "pending", "pending_dispatch" -> NotificationType.BOOKING_CONFIRMED;
                case "assigned", "accepted" -> NotificationType.COURIER_ASSIGNED;
                case "picked_up" -> NotificationType.PARCEL_PICKED_UP;
                case "in_transit" -> NotificationType.IN_TRANSIT;
                case "out_for_delivery" -> NotificationType.OUT_FOR_DELIVERY;
                case "failed", "delivery_failed" -> NotificationType.DELIVERY_FAILED;
                case "delivered" -> NotificationType.DELIVERY_SUCCESSFUL;
                default -> null;
            };
        }

        return null;
    }

    private String resolveRecipientId(Map<String, Object> payload) {
        String recipientId = getString(payload, "senderId");
        if (recipientId == null || recipientId.isBlank()) {
            recipientId = getString(payload, "customerId");
        }
        if (recipientId == null || recipientId.isBlank()) {
            recipientId = getString(payload, "recipientId");
        }
        if (recipientId == null || recipientId.isBlank()) {
            recipientId = "cust_anonymous";
        }
        return recipientId;
    }

    private String getString(Map<String, Object> map, String key) {
        Object val = map.get(key);
        return val != null ? String.valueOf(val).trim() : null;
    }
}
