package com.swiftdrop.parcel.service;

import com.swiftdrop.parcel.config.RabbitMqConfig;
import com.swiftdrop.parcel.entity.OutboxEvent;
import com.swiftdrop.parcel.entity.enums.OutboxStatus;
import com.swiftdrop.parcel.event.ParcelBookedApplicationEvent;
import com.swiftdrop.parcel.repository.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Instant;
import java.util.List;

@Component
@ConditionalOnBean(RabbitTemplate.class)
public class OutboxPublisherWorker {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisherWorker.class);
    private static final int MAX_RETRIES = 5;

    private final OutboxEventRepository outboxEventRepository;
    private final RabbitTemplate rabbitTemplate;

    public OutboxPublisherWorker(OutboxEventRepository outboxEventRepository, RabbitTemplate rabbitTemplate) {
        this.outboxEventRepository = outboxEventRepository;
        this.rabbitTemplate = rabbitTemplate;
    }

    @Scheduled(fixedDelayString = "${outbox.publisher.delay-ms:5000}", initialDelayString = "${outbox.publisher.initial-delay-ms:5000}")
    @Transactional
    public void publishPendingEvents() {
        List<OutboxEvent> pending = outboxEventRepository.findTop50ByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING);
        if (pending.isEmpty()) {
            return;
        }

        log.debug("Found {} pending parcel outbox events to publish", pending.size());

        for (OutboxEvent event : pending) {
            String routingKey = resolveRoutingKey(event.getEventType());
            try {
                rabbitTemplate.convertAndSend(RabbitMqConfig.EXCHANGE_NAME, routingKey, event.getPayload());
                event.setStatus(OutboxStatus.PUBLISHED);
                event.setPublishedAt(Instant.now());
                event.setLastError(null);
                log.info("Published parcel outbox event [id={}, type={}, routingKey={}]", event.getId(), event.getEventType(), routingKey);
            } catch (Exception ex) {
                int retries = (event.getRetryCount() != null ? event.getRetryCount() : 0) + 1;
                event.setRetryCount(retries);
                event.setLastError(ex.getMessage());
                if (retries >= MAX_RETRIES) {
                    event.setStatus(OutboxStatus.FAILED);
                    log.error("Parcel outbox event exceeded max retries [id={}, type={}], marked as FAILED: {}",
                            event.getId(), event.getEventType(), ex.getMessage());
                } else {
                    log.warn("Failed to publish parcel outbox event [id={}, retry={}/{}]: {}",
                            event.getId(), retries, MAX_RETRIES, ex.getMessage());
                }
                break; // Stop batch on broker connection failure to allow next scheduled cycle to retry
            }
        }
        outboxEventRepository.saveAll(pending);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleParcelBooked(ParcelBookedApplicationEvent event) {
        log.debug("Parcel booking transaction committed for id={}. Triggering immediate outbox publication.", event.parcelId());
        try {
            publishPendingEvents();
        } catch (Exception e) {
            log.warn("Immediate outbox publication attempt encountered an issue; scheduled worker will retry: {}", e.getMessage());
        }
    }

    private String resolveRoutingKey(String eventType) {
        if (eventType == null) {
            return RabbitMqConfig.ROUTING_KEY_PARCEL_CREATED;
        }
        return switch (eventType.toUpperCase()) {
            case "PARCEL_CREATED", "BOOKING_CREATED" -> RabbitMqConfig.ROUTING_KEY_PARCEL_CREATED;
            case "PARCEL_ASSIGNED" -> "parcel.assigned";
            case "PARCEL_PICKED_UP" -> "parcel.picked_up";
            case "PARCEL_IN_TRANSIT" -> "parcel.in_transit";
            case "PARCEL_OUT_FOR_DELIVERY" -> "parcel.out_for_delivery";
            case "PARCEL_DELIVERED" -> "parcel.delivered";
            case "PARCEL_FAILED", "PARCEL_DELIVERY_FAILED" -> "parcel.delivery_failed";
            case "PARCEL_CANCELLED" -> "parcel.cancelled";
            case "PARCEL_RETURNED" -> "parcel.returned";
            default -> "parcel." + eventType.toLowerCase();
        };
    }
}
