package com.swiftdrop.parcel.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.parcel.dto.request.CreateParcelBookingRequest;
import com.swiftdrop.parcel.entity.OutboxEvent;
import com.swiftdrop.parcel.entity.Parcel;
import com.swiftdrop.parcel.event.ParcelBookedApplicationEvent;
import com.swiftdrop.parcel.repository.ParcelRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ParcelBookingServiceTest {

    @Mock
    private ParcelRepository parcelRepository;

    @Mock
    private OutboxEventService outboxEventService;

    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    private ObjectMapper objectMapper;
    private ParcelBookingService parcelBookingService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
        parcelBookingService = new ParcelBookingService(
                parcelRepository,
                outboxEventService,
                objectMapper,
                applicationEventPublisher
        );
    }

    @Test
    void bookingSucceeds_createsParcelAndOutboxEventInSameTransaction() {
        CreateParcelBookingRequest request = new CreateParcelBookingRequest(
                "Nimal Silva",
                "+94771234567",
                "100 Galle Road, Colombo 03",
                "12 Warehouse Road, Colombo 10",
                "Fragile tea boxes",
                new BigDecimal("4.50"),
                "EXPRESS",
                true,
                "COD",
                new BigDecimal("850.00")
        );

        when(parcelRepository.save(any(Parcel.class))).thenAnswer(inv -> inv.getArgument(0));

        Parcel saved = parcelBookingService.bookParcel(request, "cust-101");

        assertNotNull(saved);
        assertEquals("cust-101", saved.getSenderId());
        assertEquals("CREATED", saved.getStatus());
        assertEquals(new BigDecimal("4.50"), saved.getWeightKg());
        assertEquals(new BigDecimal("850.00"), saved.getTotalFee());
        assertTrue(saved.getFragile());

        // Verify outbox event was saved with aggregate type and id
        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(outboxEventService).saveEvent(
                eq("PARCEL"),
                eq(saved.getId()),
                eq("PARCEL_CREATED"),
                payloadCaptor.capture()
        );

        String payload = payloadCaptor.getValue();
        assertTrue(payload.contains(saved.getId()));
        assertTrue(payload.contains(saved.getTrackingNumber()));
        assertTrue(payload.contains("100 Galle Road, Colombo 03"));
        assertTrue(payload.contains("HIGH")); // Fragile or EXPRESS implies HIGH priority

        // Verify application event published for after-commit listener
        verify(applicationEventPublisher).publishEvent(any(ParcelBookedApplicationEvent.class));
    }

    @Test
    void bookingFailsOnSave_doesNotLeavePublishableEvent() {
        CreateParcelBookingRequest request = new CreateParcelBookingRequest(
                "Nimal Silva",
                "+94771234567",
                "100 Galle Road, Colombo 03",
                "12 Warehouse Road, Colombo 10",
                "Test",
                new BigDecimal("2.00"),
                "STANDARD",
                false,
                "COD",
                new BigDecimal("500.00")
        );

        when(parcelRepository.save(any(Parcel.class))).thenThrow(new RuntimeException("Database error"));

        assertThrows(RuntimeException.class, () -> parcelBookingService.bookParcel(request, "cust-101"));

        // Outbox event must NOT be saved when parcel saving fails
        verifyNoInteractions(outboxEventService);
        verifyNoInteractions(applicationEventPublisher);
    }
}
