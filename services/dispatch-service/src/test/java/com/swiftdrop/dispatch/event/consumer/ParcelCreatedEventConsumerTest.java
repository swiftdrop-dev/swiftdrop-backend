package com.swiftdrop.dispatch.event.consumer;

import com.swiftdrop.dispatch.dto.request.CreateDispatchJobRequest;
import com.swiftdrop.dispatch.dto.response.DispatchJobResponse;
import com.swiftdrop.dispatch.entity.DispatchJob;
import com.swiftdrop.dispatch.entity.enums.DispatchPriority;
import com.swiftdrop.dispatch.entity.enums.VehicleType;
import com.swiftdrop.dispatch.event.ParcelCreatedEvent;
import com.swiftdrop.dispatch.exception.DuplicateResourceException;
import com.swiftdrop.dispatch.repository.DispatchJobRepository;
import com.swiftdrop.dispatch.service.DispatchJobService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ParcelCreatedEventConsumerTest {

    @Mock
    private DispatchJobService dispatchJobService;

    @Mock
    private DispatchJobRepository jobRepository;

    private ParcelCreatedEventConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new ParcelCreatedEventConsumer(dispatchJobService, jobRepository);
    }

    private ParcelCreatedEvent buildValidEvent() {
        ParcelCreatedEvent event = new ParcelCreatedEvent();
        event.setEventId(UUID.randomUUID().toString());
        event.setParcelId(UUID.randomUUID().toString());
        event.setTrackingNumber("SWF-2026-B1C2D3E4");
        event.setSenderId(UUID.randomUUID().toString());
        event.setPickupAddress("12 Warehouse Road, Colombo 10");
        event.setDeliveryAddress("45 Galle Road, Colombo 03");
        event.setWeightKg(new BigDecimal("3.50"));
        event.setParcelSize("STANDARD");
        event.setFragile(false);
        event.setPriority("STANDARD");
        return event;
    }

    @Test
    void handleParcelCreated_validEvent_createsUnassignedJob() {
        ParcelCreatedEvent event = buildValidEvent();

        when(jobRepository.existsByParcelId(any(UUID.class))).thenReturn(false);
        when(jobRepository.findByTrackingNumber(event.getTrackingNumber())).thenReturn(Optional.empty());

        consumer.handleParcelCreated(event);

        ArgumentCaptor<CreateDispatchJobRequest> captor = ArgumentCaptor.forClass(CreateDispatchJobRequest.class);
        verify(dispatchJobService).createJob(captor.capture());

        CreateDispatchJobRequest req = captor.getValue();
        assertEquals(event.getTrackingNumber(), req.getTrackingNumber());
        assertEquals(new BigDecimal("3.50"), req.getParcelWeightKg());
        assertEquals("Colombo", req.getPickupCity());
        assertEquals(VehicleType.MOTORCYCLE, req.getRequiredVehicleType());
        assertEquals(DispatchPriority.STANDARD, req.getPriority());
    }

    @Test
    void handleParcelCreated_fragileOrExpress_setsHighPriority() {
        ParcelCreatedEvent event = buildValidEvent();
        event.setFragile(true);
        event.setWeightKg(new BigDecimal("25.00")); // Bulk weight

        when(jobRepository.existsByParcelId(any(UUID.class))).thenReturn(false);
        when(jobRepository.findByTrackingNumber(event.getTrackingNumber())).thenReturn(Optional.empty());

        consumer.handleParcelCreated(event);

        ArgumentCaptor<CreateDispatchJobRequest> captor = ArgumentCaptor.forClass(CreateDispatchJobRequest.class);
        verify(dispatchJobService).createJob(captor.capture());

        CreateDispatchJobRequest req = captor.getValue();
        assertEquals(DispatchPriority.HIGH, req.getPriority());
        assertEquals(VehicleType.VAN, req.getRequiredVehicleType());
    }

    @Test
    void handleParcelCreated_duplicateParcelId_skipsJobCreationIdempotently() {
        ParcelCreatedEvent event = buildValidEvent();

        when(jobRepository.existsByParcelId(any(UUID.class))).thenReturn(true);

        consumer.handleParcelCreated(event);

        verify(dispatchJobService, never()).createJob(any());
    }

    @Test
    void handleParcelCreated_duplicateTrackingNumber_skipsJobCreationIdempotently() {
        ParcelCreatedEvent event = buildValidEvent();

        when(jobRepository.existsByParcelId(any(UUID.class))).thenReturn(false);
        when(jobRepository.findByTrackingNumber(event.getTrackingNumber()))
                .thenReturn(Optional.of(new DispatchJob()));

        consumer.handleParcelCreated(event);

        verify(dispatchJobService, never()).createJob(any());
    }

    @Test
    void handleParcelCreated_concurrentDuplicate_handledIdempotentlyViaDuplicateResource() {
        ParcelCreatedEvent event = buildValidEvent();

        when(jobRepository.existsByParcelId(any(UUID.class))).thenReturn(false);
        when(jobRepository.findByTrackingNumber(event.getTrackingNumber())).thenReturn(Optional.empty());
        when(dispatchJobService.createJob(any(CreateDispatchJobRequest.class)))
                .thenThrow(new DuplicateResourceException("Job already exists"));

        // Must not throw exception
        assertDoesNotThrow(() -> consumer.handleParcelCreated(event));
    }

    @Test
    void handleParcelCreated_concurrentDuplicate_handledIdempotentlyViaDataIntegrity() {
        ParcelCreatedEvent event = buildValidEvent();

        when(jobRepository.existsByParcelId(any(UUID.class))).thenReturn(false);
        when(jobRepository.findByTrackingNumber(event.getTrackingNumber())).thenReturn(Optional.empty());
        when(dispatchJobService.createJob(any(CreateDispatchJobRequest.class)))
                .thenThrow(new DataIntegrityViolationException("Unique constraint violation"));

        // Must not throw exception
        assertDoesNotThrow(() -> consumer.handleParcelCreated(event));
    }

    @Test
    void handleParcelCreated_invalidEventMissingFields_rejectsWithoutRequeue() {
        ParcelCreatedEvent event = new ParcelCreatedEvent();
        event.setParcelId(""); // blank
        event.setTrackingNumber(null); // missing

        assertThrows(AmqpRejectAndDontRequeueException.class,
                () -> consumer.handleParcelCreated(event));

        verify(dispatchJobService, never()).createJob(any());
    }

    @Test
    void handleParcelCreated_nullEvent_rejectsWithoutRequeue() {
        assertThrows(AmqpRejectAndDontRequeueException.class,
                () -> consumer.handleParcelCreated(null));

        verify(dispatchJobService, never()).createJob(any());
    }

    @Test
    void handleParcelCreated_transientDatabaseFailure_propagatesForRetry() {
        ParcelCreatedEvent event = buildValidEvent();

        when(jobRepository.existsByParcelId(any(UUID.class))).thenReturn(false);
        when(jobRepository.findByTrackingNumber(event.getTrackingNumber())).thenReturn(Optional.empty());
        when(dispatchJobService.createJob(any(CreateDispatchJobRequest.class)))
                .thenThrow(new RuntimeException("Transient connection failure"));

        assertThrows(RuntimeException.class, () -> consumer.handleParcelCreated(event));
    }
}
