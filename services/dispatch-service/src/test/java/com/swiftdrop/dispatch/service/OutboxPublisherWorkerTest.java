package com.swiftdrop.dispatch.service;

import com.swiftdrop.dispatch.config.RabbitMqConfig;
import com.swiftdrop.dispatch.entity.OutboxEvent;
import com.swiftdrop.dispatch.entity.enums.OutboxStatus;
import com.swiftdrop.dispatch.repository.OutboxEventRepository;
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
        OutboxEvent event = new OutboxEvent("DISPATCH_JOB", "job-101", "JOB_CREATED", "{\"jobId\":\"job-101\"}");
        event.setId(UUID.randomUUID());
        when(outboxEventRepository.findTop50ByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING))
                .thenReturn(List.of(event));

        worker.publishPendingEvents();

        verify(rabbitTemplate).convertAndSend(
                eq(RabbitMqConfig.EXCHANGE_NAME),
                eq("dispatch.job.created"),
                eq("{\"jobId\":\"job-101\"}")
        );
        assertEquals(OutboxStatus.PUBLISHED, event.getStatus());
        assertNotNull(event.getPublishedAt());
        verify(outboxEventRepository).saveAll(List.of(event));
    }

    @Test
    void publishPendingEvents_handlesBrokerExceptionAndIncrementsRetry() {
        OutboxEvent event = new OutboxEvent("DISPATCH_JOB", "job-101", "COURIER_ASSIGNED", "{\"jobId\":\"job-101\"}");
        event.setId(UUID.randomUUID());
        when(outboxEventRepository.findTop50ByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING))
                .thenReturn(List.of(event));
        doThrow(new AmqpException("Broker connection error"))
                .when(rabbitTemplate).convertAndSend(anyString(), anyString(), anyString());

        worker.publishPendingEvents();

        assertEquals(OutboxStatus.PENDING, event.getStatus());
        assertEquals(1, event.getRetryCount());
        assertTrue(event.getLastError().contains("Broker connection error"));
        verify(outboxEventRepository).saveAll(List.of(event));
    }
}
