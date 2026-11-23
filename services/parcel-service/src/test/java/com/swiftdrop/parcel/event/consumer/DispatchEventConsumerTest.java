package com.swiftdrop.parcel.event.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.parcel.entity.Parcel;
import com.swiftdrop.parcel.entity.ParcelAssignment;
import com.swiftdrop.parcel.event.ParcelBookedApplicationEvent;
import com.swiftdrop.parcel.repository.ParcelRepository;
import com.swiftdrop.parcel.service.AssignmentOutcome;
import com.swiftdrop.parcel.service.OutboxEventService;
import com.swiftdrop.parcel.service.ParcelAssignmentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DispatchEventConsumerTest {

    @Mock
    private ParcelAssignmentService assignmentService;

    @Mock
    private ParcelRepository parcelRepository;

    @Mock
    private OutboxEventService outboxEventService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private DispatchEventConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new DispatchEventConsumer(
                assignmentService,
                parcelRepository,
                outboxEventService,
                objectMapper,
                eventPublisher
        );
    }

    @Test
    void handleDispatchEvent_successfullyAssignsParcelAndUpdatesStatus() {
        String json = """
                {
                    "eventType": "COURIER_ACCEPTED",
                    "parcelId": "prc_001",
                    "courierId": "cou_001",
                    "trackingNumber": "SWF-2026-TEST"
                }
                """;

        Parcel parcel = new Parcel("prc_001", null, "SWF-2026-TEST", "CREATED", null, "Recipient", "0711111111", "Address");
        when(parcelRepository.findById("prc_001")).thenReturn(Optional.of(parcel));
        when(assignmentService.assign(eq("prc_001"), eq("cou_001"), any(), any()))
                .thenReturn(new AssignmentOutcome(new ParcelAssignment("prc_001", "cou_001", Instant.now(), "DISPATCH", "Accepted"), true));

        consumer.handleDispatchEvent(json);

        verify(assignmentService).assign("prc_001", "cou_001", "DISPATCH_SERVICE", "Courier accepted dispatch assignment");
        assertEquals("assigned", parcel.getStatus());
        verify(parcelRepository).saveAndFlush(parcel);
        verify(outboxEventService).saveEvent(eq("PARCEL"), eq("prc_001"), eq("PARCEL_ASSIGNED"), any());
        verify(eventPublisher).publishEvent(any(ParcelBookedApplicationEvent.class));
    }

    @Test
    void handleDispatchEvent_handlesMissingParcelGracefully() {
        String json = """
                {
                    "eventType": "COURIER_ACCEPTED",
                    "parcelId": "prc_missing",
                    "courierId": "cou_001"
                }
                """;

        when(parcelRepository.findById("prc_missing")).thenReturn(Optional.empty());

        consumer.handleDispatchEvent(json);

        verify(assignmentService).assign("prc_missing", "cou_001", "DISPATCH_SERVICE", "Courier accepted dispatch assignment");
        verify(parcelRepository, never()).saveAndFlush(any());
    }

    @Test
    void handleDispatchEvent_missingFieldsIgnored() {
        String json = """
                {
                    "eventType": "COURIER_ACCEPTED"
                }
                """;

        consumer.handleDispatchEvent(json);

        verifyNoInteractions(assignmentService);
        verifyNoInteractions(parcelRepository);
    }

    @Test
    void handleDispatchEvent_rejectsNullMessage() {
        assertThrows(AmqpRejectAndDontRequeueException.class, () -> consumer.handleDispatchEvent(null));
    }
}
