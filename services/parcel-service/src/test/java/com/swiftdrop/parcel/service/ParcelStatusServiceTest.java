package com.swiftdrop.parcel.service;

import com.swiftdrop.parcel.entity.Parcel;
import com.swiftdrop.parcel.entity.ParcelAssignment;
import com.swiftdrop.parcel.entity.ParcelStatusHistory;
import com.swiftdrop.parcel.exception.CourierAssignmentMismatchException;
import com.swiftdrop.parcel.exception.ParcelNotFoundException;
import com.swiftdrop.parcel.exception.ParcelStatusConflictException;
import com.swiftdrop.parcel.repository.ParcelAssignmentRepository;
import com.swiftdrop.parcel.repository.ParcelRepository;
import com.swiftdrop.parcel.repository.ParcelStatusHistoryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ParcelStatusServiceTest {
    private static final Instant NOW = Instant.parse("2026-11-14T06:30:00Z");

    @Mock private ParcelRepository parcelRepository;
    @Mock private ParcelAssignmentRepository assignmentRepository;
    @Mock private ParcelStatusHistoryRepository historyRepository;

    private final ParcelStatusTransitionValidator validator = new ParcelStatusTransitionValidator();

    @Test
    void updatesPendingToInTransitAndRecordsTrustedActor() {
        Parcel parcel = setupParcel("pending", "cou_001");
        when(historyRepository.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));

        ParcelStatusHistory history = serviceWithFixedClock().updateStatus(
                "prc_001", "cou_001", "in_transit", null);

        assertEquals("in_transit", parcel.getStatus());
        assertEquals("pending", history.getPreviousStatus());
        assertEquals("in_transit", history.getNewStatus());
        assertEquals("cou_001", history.getActorId());
        assertEquals(NOW, history.getChangedAt());
        assertNull(history.getFailureReason());
        assertNull(parcel.getDeliveredAt());
        inOrder(parcelRepository, assignmentRepository)
                .verify(parcelRepository).findByIdForUpdate("prc_001");
        verify(parcelRepository).saveAndFlush(parcel);
        verify(historyRepository).saveAndFlush(any(ParcelStatusHistory.class));
    }

    @Test
    void requiresFailureReasonWhenTransitioningToFailed() {
        setupParcel("in_transit", "cou_001");
        assertThrows(ParcelStatusConflictException.class,
                () -> serviceWithFixedClock().updateStatus("prc_001", "cou_001", "failed", null));
        assertThrows(ParcelStatusConflictException.class,
                () -> serviceWithFixedClock().updateStatus("prc_001", "cou_001", "failed", "  "));
        assertThrows(ParcelStatusConflictException.class,
                () -> serviceWithFixedClock().updateStatus("prc_001", "cou_001", "failed", "x".repeat(1001)));
        verify(historyRepository, never()).saveAndFlush(any());
    }

    @Test
    void trimsAndPersistsValidFailureReason() {
        Parcel parcel = setupParcel("in_transit", "cou_001");
        when(historyRepository.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));

        ParcelStatusHistory history = serviceWithFixedClock().updateStatus(
                "prc_001", "cou_001", "failed", "  Recipient unavailable  ");

        assertEquals("failed", parcel.getStatus());
        assertEquals("Recipient unavailable", history.getFailureReason());
    }

    @Test
    void rejectsMissingParcelAndMissingAssignment() {
        when(parcelRepository.findByIdForUpdate("missing")).thenReturn(Optional.empty());
        assertThrows(ParcelNotFoundException.class,
                () -> serviceWithFixedClock().updateStatus("missing", "cou_001", "in_transit", null));

        Parcel parcel = new Parcel("prc_unassigned", null, "SWF-TEST", "pending", null,
                "Recipient", "0700000000", "Address");
        when(parcelRepository.findByIdForUpdate("prc_unassigned")).thenReturn(Optional.of(parcel));
        when(assignmentRepository.findActiveByParcelIdForUpdate("prc_unassigned"))
                .thenReturn(Optional.empty());
        assertThrows(ParcelStatusConflictException.class,
                () -> serviceWithFixedClock().updateStatus("prc_unassigned", "cou_001", "in_transit", null));
        verify(historyRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsWrongCourierBeforeChangingParcel() {
        Parcel parcel = setupParcel("pending", "cou_assigned");
        assertThrows(CourierAssignmentMismatchException.class,
                () -> serviceWithFixedClock().updateStatus("prc_001", "cou_other", "in_transit", null));
        assertEquals("pending", parcel.getStatus());
        verify(historyRepository, never()).saveAndFlush(any());
    }

    private Parcel setupParcel(String status, String courierId) {
        Parcel parcel = new Parcel("prc_001", courierId, "SWF-TEST", status, null,
                "Recipient", "0700000000", "Address");
        when(parcelRepository.findByIdForUpdate("prc_001")).thenReturn(Optional.of(parcel));
        when(assignmentRepository.findActiveByParcelIdForUpdate("prc_001"))
                .thenReturn(Optional.of(new ParcelAssignment("prc_001", courierId, NOW, null, null)));
        return parcel;
    }

    private ParcelStatusService serviceWithFixedClock() {
        return new ParcelStatusService(parcelRepository, assignmentRepository, historyRepository,
                validator, Clock.fixed(NOW, ZoneOffset.UTC));
    }
}
