package com.swiftdrop.merchant.service;

import com.swiftdrop.merchant.config.RabbitMqConfig;
import com.swiftdrop.merchant.entity.OutboxEvent;
import com.swiftdrop.merchant.entity.enums.OutboxStatus;
import com.swiftdrop.merchant.repository.OutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxPublisherWorkerTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private RabbitTemplate rabbitTemplate;

    private OutboxPublisherWorker worker;

    @BeforeEach
    void setUp() {
        worker = new OutboxPublisherWorker(outboxEventRepository, rabbitTemplate);
    }

    @Test
    void publishPendingEvents_successfullyPublishesAndMarksPublished() {
        OutboxEvent event = new OutboxEvent("MERCHANT", "m-123", "MERCHANT_REGISTERED", "{\"id\":\"m-123\"}");
        event.setId(UUID.randomUUID());
        when(outboxEventRepository.findTop50ByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING))
                .thenReturn(List.of(event));

        worker.publishPendingEvents();

        verify(rabbitTemplate).convertAndSend(
                eq(RabbitMqConfig.EXCHANGE_NAME),
                eq("merchant.merchant.registered"),
                eq("{\"id\":\"m-123\"}")
        );
        assertEquals(OutboxStatus.PUBLISHED, event.getStatus());
        assertNotNull(event.getPublishedAt());
        verify(outboxEventRepository).saveAll(List.of(event));
    }

    @Test
    void publishPendingEvents_handlesBrokerExceptionAndIncrementsRetry() {
        OutboxEvent event = new OutboxEvent("MERCHANT", "m-123", "MERCHANT_APPROVED", "{\"id\":\"m-123\"}");
        event.setId(UUID.randomUUID());
        when(outboxEventRepository.findTop50ByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING))
                .thenReturn(List.of(event));
        doThrow(new AmqpException("Broker unavailable"))
                .when(rabbitTemplate).convertAndSend(anyString(), anyString(), anyString());

        worker.publishPendingEvents();

        assertEquals(OutboxStatus.PENDING, event.getStatus());
        assertEquals(1, event.getRetryCount());
        assertTrue(event.getLastError().contains("Broker unavailable"));
        verify(outboxEventRepository).saveAll(List.of(event));
    }
}
