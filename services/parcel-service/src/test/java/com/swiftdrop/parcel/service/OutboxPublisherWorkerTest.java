package com.swiftdrop.parcel.service;

import com.swiftdrop.parcel.config.RabbitMqConfig;
import com.swiftdrop.parcel.entity.OutboxEvent;
import com.swiftdrop.parcel.entity.enums.OutboxStatus;
import com.swiftdrop.parcel.event.ParcelBookedApplicationEvent;
import com.swiftdrop.parcel.repository.OutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.Collections;
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
        OutboxEvent event = new OutboxEvent("PARCEL", "prc-101", "PARCEL_CREATED", "{\"parcelId\":\"prc-101\"}");
        event.setId(UUID.randomUUID());

        when(outboxEventRepository.findTop50ByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING))
                .thenReturn(List.of(event));

        worker.publishPendingEvents();

        verify(rabbitTemplate).convertAndSend(
                eq(RabbitMqConfig.EXCHANGE_NAME),
                eq(RabbitMqConfig.ROUTING_KEY_PARCEL_CREATED),
                eq("{\"parcelId\":\"prc-101\"}")
        );

        assertEquals(OutboxStatus.PUBLISHED, event.getStatus());
        assertNotNull(event.getPublishedAt());
        assertNull(event.getLastError());
        verify(outboxEventRepository).saveAll(List.of(event));
    }

    @Test
    void publishPendingEvents_handlesBrokerExceptionAndRetainsForRetry() {
        OutboxEvent event = new OutboxEvent("PARCEL", "prc-102", "PARCEL_CREATED", "{\"parcelId\":\"prc-102\"}");
        event.setId(UUID.randomUUID());
        event.setRetryCount(1);

        when(outboxEventRepository.findTop50ByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING))
                .thenReturn(List.of(event));
        doThrow(new AmqpException("Broker connection refused"))
                .when(rabbitTemplate).convertAndSend(anyString(), anyString(), anyString());

        worker.publishPendingEvents();

        assertEquals(OutboxStatus.PENDING, event.getStatus());
        assertEquals(2, event.getRetryCount());
        assertEquals("Broker connection refused", event.getLastError());
        assertNull(event.getPublishedAt());
        verify(outboxEventRepository).saveAll(List.of(event));
    }

    @Test
    void publishPendingEvents_marksFailedWhenMaxRetriesExceeded() {
        OutboxEvent event = new OutboxEvent("PARCEL", "prc-103", "PARCEL_CREATED", "{\"parcelId\":\"prc-103\"}");
        event.setId(UUID.randomUUID());
        event.setRetryCount(4); // 4 + 1 = 5 (MAX_RETRIES)

        when(outboxEventRepository.findTop50ByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING))
                .thenReturn(List.of(event));
        doThrow(new AmqpException("Permanent error"))
                .when(rabbitTemplate).convertAndSend(anyString(), anyString(), anyString());

        worker.publishPendingEvents();

        assertEquals(OutboxStatus.FAILED, event.getStatus());
        assertEquals(5, event.getRetryCount());
        assertEquals("Permanent error", event.getLastError());
        verify(outboxEventRepository).saveAll(List.of(event));
    }

    @Test
    void handleParcelBooked_triggersImmediatePublicationAfterCommit() {
        when(outboxEventRepository.findTop50ByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING))
                .thenReturn(Collections.emptyList());

        worker.handleParcelBooked(new ParcelBookedApplicationEvent("prc-104"));

        verify(outboxEventRepository).findTop50ByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING);
    }
}
