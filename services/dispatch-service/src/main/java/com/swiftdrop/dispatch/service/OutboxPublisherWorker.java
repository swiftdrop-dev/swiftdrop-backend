package com.swiftdrop.dispatch.service;

import com.swiftdrop.dispatch.config.RabbitMqConfig;
import com.swiftdrop.dispatch.entity.OutboxEvent;
import com.swiftdrop.dispatch.entity.enums.OutboxStatus;
import com.swiftdrop.dispatch.repository.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

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

        log.debug("Found {} pending dispatch outbox events to publish", pending.size());

        for (OutboxEvent event : pending) {
            String routingKey = "dispatch." + event.getEventType().toLowerCase().replace('_', '.');
            try {
                rabbitTemplate.convertAndSend(RabbitMqConfig.EXCHANGE_NAME, routingKey, event.getPayload());
                if ("DispatchJobAcceptedEvent".equalsIgnoreCase(event.getEventType())) {
                    rabbitTemplate.convertAndSend(RabbitMqConfig.EXCHANGE_NAME, "courier.accepted", event.getPayload());
                } else if ("DispatchJobAssignedEvent".equalsIgnoreCase(event.getEventType())) {
                    rabbitTemplate.convertAndSend(RabbitMqConfig.EXCHANGE_NAME, "dispatch.assigned", event.getPayload());
                } else if ("DispatchJobDeclinedEvent".equalsIgnoreCase(event.getEventType())) {
                    rabbitTemplate.convertAndSend(RabbitMqConfig.EXCHANGE_NAME, "courier.declined", event.getPayload());
                }
                event.setStatus(OutboxStatus.PUBLISHED);
                event.setPublishedAt(Instant.now());
                event.setLastError(null);
                log.info("Published dispatch outbox event [id={}, type={}, routingKey={}]", event.getId(), event.getEventType(), routingKey);
            } catch (Exception ex) {
                int retries = (event.getRetryCount() != null ? event.getRetryCount() : 0) + 1;
                event.setRetryCount(retries);
                event.setLastError(ex.getMessage());
                if (retries >= MAX_RETRIES) {
                    event.setStatus(OutboxStatus.FAILED);
                    log.error("Dispatch outbox event exceeded max retries [id={}, type={}], marked as FAILED: {}",
                            event.getId(), event.getEventType(), ex.getMessage());
                } else {
                    log.warn("Failed to publish dispatch outbox event [id={}, retry={}/{}]: {}",
                            event.getId(), retries, MAX_RETRIES, ex.getMessage());
                }
                break; // Stop batch on broker connection failure to allow next scheduled cycle to retry
            }
        }
        outboxEventRepository.saveAll(pending);
    }
}
