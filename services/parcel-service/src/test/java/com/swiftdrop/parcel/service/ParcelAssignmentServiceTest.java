package com.swiftdrop.parcel.service;

import com.swiftdrop.parcel.entity.ParcelAssignment;
import com.swiftdrop.parcel.repository.ParcelDeliveryOtpChallengeRepository;
import com.swiftdrop.parcel.entity.Parcel;
import com.swiftdrop.parcel.exception.AssignmentConflictException;
import com.swiftdrop.parcel.exception.ParcelNotFoundException;
import com.swiftdrop.parcel.repository.ParcelAssignmentRepository;
import com.swiftdrop.parcel.repository.ParcelRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.sql.SQLException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ParcelAssignmentServiceTest {
    @Mock
    private ParcelAssignmentRepository assignmentRepository;

    @Mock
    private ParcelRepository parcelRepository;

    @Mock
    private ParcelDeliveryOtpChallengeRepository otpChallengeRepository;

    @InjectMocks
    private ParcelAssignmentService assignmentService;

    @Test
    void firstAssignmentSynchronizesParcelAndCreatesHistory() {
        Parcel parcel = new Parcel("prc_001", null);
        when(parcelRepository.findByIdForUpdate("prc_001")).thenReturn(Optional.of(parcel));
        when(assignmentRepository.findActiveByParcelIdForUpdate("prc_001"))
                .thenReturn(Optional.empty());
        when(assignmentRepository.saveAndFlush(any(ParcelAssignment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var outcome = assignmentService.assign("prc_001", "cou_001", "dispatcher-1", "Initial assignment");
        ParcelAssignment assignment = outcome.assignment();

        assertEquals("cou_001", parcel.getCourierId());
        assertEquals("prc_001", assignment.getParcelId());
        assertEquals("cou_001", assignment.getCourierId());
        assertNotNull(assignment.getAssignedAt());
        org.junit.jupiter.api.Assertions.assertTrue(outcome.created());
        inOrder(parcelRepository, assignmentRepository)
                .verify(parcelRepository).findByIdForUpdate("prc_001");
        verify(parcelRepository).saveAndFlush(parcel);
        verify(assignmentRepository).saveAndFlush(any(ParcelAssignment.class));
    }

    @Test
    void repeatedAssignmentToSameCourierDoesNotCreateDuplicateActiveHistory() {
        ParcelAssignment active = new ParcelAssignment("prc_001", "cou_001", Instant.now(), "dispatcher-1", null);
        Parcel parcel = new Parcel("prc_001", "cou_001");
        when(parcelRepository.findByIdForUpdate("prc_001")).thenReturn(Optional.of(parcel));
        when(assignmentRepository.findActiveByParcelIdForUpdate("prc_001"))
                .thenReturn(Optional.of(active));

        var result = assignmentService.assign("prc_001", "cou_001", "dispatcher-2", "Retry");

        assertEquals(active, result.assignment());
        org.junit.jupiter.api.Assertions.assertFalse(result.created());
        verify(assignmentRepository, never()).save(any(ParcelAssignment.class));
        verify(assignmentRepository, never()).saveAndFlush(any(ParcelAssignment.class));
        verify(parcelRepository, never()).saveAndFlush(any(Parcel.class));
    }

    @Test
    void reassignmentClosesPriorAssignmentAndAddsNewHistoryRow() {
        ParcelAssignment previous = new ParcelAssignment("prc_001", "cou_001",
                Instant.now().minusSeconds(600), "dispatcher-1", "Original");
        Parcel parcel = new Parcel("prc_001", "cou_001");
        when(parcelRepository.findByIdForUpdate("prc_001")).thenReturn(Optional.of(parcel));
        when(assignmentRepository.findActiveByParcelIdForUpdate("prc_001"))
                .thenReturn(Optional.of(previous));
        when(assignmentRepository.saveAndFlush(any(ParcelAssignment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var outcome = assignmentService.assign("prc_001", "cou_002", "dispatcher-2", "Courier reassigned");
        ParcelAssignment next = outcome.assignment();

        assertEquals("cou_002", parcel.getCourierId());
        assertNotNull(previous.getUnassignedAt());
        assertEquals("cou_002", next.getCourierId());
        assertEquals("Courier reassigned", next.getReason());
        assertNotNull(next.getAssignedAt());
        org.junit.jupiter.api.Assertions.assertTrue(outcome.created());

        ArgumentCaptor<ParcelAssignment> saved = ArgumentCaptor.forClass(ParcelAssignment.class);
        verify(assignmentRepository, org.mockito.Mockito.times(2)).saveAndFlush(saved.capture());
        assertEquals(previous, saved.getAllValues().get(0));
        assertEquals(next, saved.getAllValues().get(1));
        inOrder(parcelRepository, assignmentRepository)
                .verify(parcelRepository).findByIdForUpdate("prc_001");
        verify(parcelRepository).saveAndFlush(parcel);
    }

    @Test
    void legacyCurrentCourierWithoutHistoryStartsHistoryAtTheNewAssignmentOperation() {
        Parcel legacyParcel = new Parcel("prc_legacy", "cou_legacy");
        when(parcelRepository.findByIdForUpdate("prc_legacy")).thenReturn(Optional.of(legacyParcel));
        when(assignmentRepository.findActiveByParcelIdForUpdate("prc_legacy"))
                .thenReturn(Optional.empty());
        when(assignmentRepository.saveAndFlush(any(ParcelAssignment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Instant beforeRequest = Instant.now();
        ParcelAssignment created = assignmentService.assign(
                "prc_legacy", "cou_legacy", "dispatcher-1", "Legacy courier confirmed today").assignment();

        assertEquals("cou_legacy", created.getCourierId());
        assertNotNull(created.getAssignedAt());
        org.junit.jupiter.api.Assertions.assertFalse(created.getAssignedAt().isBefore(beforeRequest));
        assertEquals("Legacy courier confirmed today", created.getReason());
        verify(assignmentRepository).saveAndFlush(any(ParcelAssignment.class));
    }

    @Test
    void missingParcelFailsBeforeAnyAssignmentHistoryWrite() {
        when(parcelRepository.findByIdForUpdate("missing")).thenReturn(Optional.empty());

        assertThrows(ParcelNotFoundException.class,
                () -> assignmentService.assign("missing", "cou_001", "dispatcher-1", "Assign"));

        verify(assignmentRepository, never()).saveAndFlush(any(ParcelAssignment.class));
    }

    @Test
    void uniqueIndexViolationIsTranslatedToAssignmentConflict() {
        Parcel parcel = new Parcel("prc_001", null);
        when(parcelRepository.findByIdForUpdate("prc_001")).thenReturn(Optional.of(parcel));
        when(assignmentRepository.findActiveByParcelIdForUpdate("prc_001"))
                .thenReturn(Optional.empty());
        when(assignmentRepository.saveAndFlush(any(ParcelAssignment.class)))
                .thenThrow(new org.springframework.dao.DataIntegrityViolationException(
                        "unique index violation", new SQLException("duplicate active assignment", "23505")));

        assertThrows(AssignmentConflictException.class,
                () -> assignmentService.assign("prc_001", "cou_001", "dispatcher-1", "Assign"));
    }

    @Test
    void assignmentLocksParcelRowBeforeReadingHistory() {
        Parcel parcel = new Parcel("prc_001", null);
        when(parcelRepository.findByIdForUpdate("prc_001")).thenReturn(Optional.of(parcel));
        when(assignmentRepository.findActiveByParcelIdForUpdate("prc_001"))
                .thenReturn(Optional.empty());
        when(assignmentRepository.saveAndFlush(any(ParcelAssignment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        assignmentService.assign("prc_001", "cou_001", "dispatcher-1", "Assign");

        var order = inOrder(parcelRepository, assignmentRepository);
        order.verify(parcelRepository).findByIdForUpdate("prc_001");
        order.verify(assignmentRepository).findActiveByParcelIdForUpdate("prc_001");
    }
}
