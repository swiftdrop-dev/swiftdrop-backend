package com.swiftdrop.tracking.event.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.tracking.entity.TrackingEvent;
import com.swiftdrop.tracking.repository.TrackingEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TrackingLifecycleEventConsumerTest {

    @Mock
    private TrackingEventRepository repository;

    private ObjectMapper objectMapper;
    private TrackingLifecycleEventConsumer consumer;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        consumer = new TrackingLifecycleEventConsumer(repository, objectMapper);
    }

    @Test
    void handleEvent_PersistsNewTrackingEvent_WithCustomerId() throws Exception {
        Map<String, Object> payload = Map.of(
                "eventId", "evt-unique-123",
                "eventType", "PARCEL_CREATED",
                "parcelId", "prc_abc",
                "trackingNumber", "TRK-001",
                "senderId", "cust-42",
                "occurredAt", "2026-11-22T12:00:00Z",
                "pickupAddress", Map.of("city", "Colombo")
        );

        when(repository.existsByEventId("evt-unique-123")).thenReturn(false);

        consumer.handleDeliveryLifecycleEvent(objectMapper.writeValueAsString(payload));

        ArgumentCaptor<TrackingEvent> captor = ArgumentCaptor.forClass(TrackingEvent.class);
        verify(repository).save(captor.capture());

        TrackingEvent saved = captor.getValue();
        assertThat(saved.getEventId()).isEqualTo("evt-unique-123");
        assertThat(saved.getTrackingNumber()).isEqualTo("TRK-001");
        assertThat(saved.getParcelId()).isEqualTo("prc_abc");
        assertThat(saved.getCustomerId()).isEqualTo("cust-42");
        assertThat(saved.getStatus()).isEqualTo("pending");
        assertThat(saved.getTitle()).isEqualTo("Booking Confirmed");
        assertThat(saved.getLocation()).isEqualTo("Colombo");
    }

    @Test
    void handleEvent_SkipsDuplicateEventId_Idempotently() throws Exception {
        Map<String, Object> payload = Map.of(
                "eventId", "evt-existing-456",
                "eventType", "COURIER_ACCEPTED",
                "parcelId", "prc_abc",
                "trackingNumber", "TRK-001"
        );

        when(repository.existsByEventId("evt-existing-456")).thenReturn(true);

        consumer.handleDeliveryLifecycleEvent(objectMapper.writeValueAsString(payload));

        verify(repository, never()).save(any());
    }

    @Test
    void handleEvent_ThrowsAmqpReject_WhenPayloadIsNull() {
        assertThatThrownBy(() -> consumer.handleDeliveryLifecycleEvent(null))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
    }

    @Test
    void handleEvent_ThrowsAmqpReject_WhenMissingIdentifiers() throws Exception {
        Map<String, Object> payload = Map.of(
                "eventType", "COURIER_ACCEPTED",
                "notes", "no tracking number or parcelId"
        );

        String json = objectMapper.writeValueAsString(payload);
        assertThatThrownBy(() -> consumer.handleDeliveryLifecycleEvent(json))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
    }

    @Test
    void handleEvent_IgnoresUnknownNonLifecycleEvent_DoesNotCreateFakeMilestone() throws Exception {
        Map<String, Object> payload = Map.of(
                "eventId", "evt-unknown-999",
                "eventType", "CUSTOMER_FEEDBACK_SUBMITTED",
                "parcelId", "prc-123",
                "trackingNumber", "TRK-123",
                "occurredAt", "2026-11-22T12:00:00Z"
        );

        when(repository.existsByEventId("evt-unknown-999")).thenReturn(false);

        consumer.handleDeliveryLifecycleEvent(objectMapper.writeValueAsString(payload));

        // Unknown non-lifecycle events must NOT create a fake milestone
        verify(repository, never()).save(any());
    }

    @Test
    void handleEvent_DeserializesActualProducerPayloads() throws Exception {
        // Actual producer payload from dispatch-service
        String dispatchPayload = """
            {
                "eventId": "evt-disp-1",
                "eventType": "COURIER_ACCEPTED",
                "eventVersion": 1,
                "occurredAt": "2026-11-22T15:00:00Z",
                "jobId": 10,
                "parcelId": "prc-disp-100",
                "courierId": 5,
                "trackingNumber": "TRK-DISP-100",
                "status": "ASSIGNED"
            }
            """;

        when(repository.existsByEventId("evt-disp-1")).thenReturn(false);

        consumer.handleDeliveryLifecycleEvent(dispatchPayload);

        ArgumentCaptor<TrackingEvent> captor = ArgumentCaptor.forClass(TrackingEvent.class);
        verify(repository).save(captor.capture());

        TrackingEvent saved = captor.getValue();
        assertThat(saved.getEventId()).isEqualTo("evt-disp-1");
        assertThat(saved.getTrackingNumber()).isEqualTo("TRK-DISP-100");
        assertThat(saved.getCourierId()).isEqualTo("5");
        assertThat(saved.getStatus()).isEqualTo("assigned");
    }
}
