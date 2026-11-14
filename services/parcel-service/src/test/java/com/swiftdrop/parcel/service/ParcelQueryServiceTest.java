package com.swiftdrop.parcel.service;

import com.swiftdrop.parcel.entity.Parcel;
import com.swiftdrop.parcel.entity.ParcelAssignment;
import com.swiftdrop.parcel.exception.InvalidRequestException;
import com.swiftdrop.parcel.exception.ParcelNotFoundException;
import com.swiftdrop.parcel.repository.ParcelAssignmentRepository;
import com.swiftdrop.parcel.repository.ParcelRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ParcelQueryServiceTest {
    @Mock
    private ParcelRepository parcelRepository;

    @Mock
    private ParcelAssignmentRepository assignmentRepository;

    private ParcelQueryService service() {
        return new ParcelQueryService(parcelRepository, assignmentRepository);
    }

    @Test
    void assignedDateUsesColomboStartInclusiveAndNextDayExclusiveBoundaries() {
        LocalDate date = LocalDate.parse("2026-11-14");
        Instant expectedStart = date.atStartOfDay(ZoneId.of("Asia/Colombo")).toInstant();
        Instant expectedEnd = date.plusDays(1).atStartOfDay(ZoneId.of("Asia/Colombo")).toInstant();
        ParcelAssignment assignment = new ParcelAssignment("prc_001", "cou_001", expectedStart, null, null);
        when(assignmentRepository
                .findByCourierIdAndAssignedAtGreaterThanEqualAndAssignedAtLessThanAndUnassignedAtIsNullOrderByAssignedAtAscIdAsc(
                        "cou_001", expectedStart, expectedEnd, PageRequest.of(0, 50)))
                .thenReturn(new PageImpl<>(List.of(assignment), PageRequest.of(0, 50), 1));
        Parcel parcel = parcel("prc_001", "cou_001", "2026-11-14");
        when(parcelRepository.findByIdInAndCourierId(List.of("prc_001"), "cou_001"))
                .thenReturn(List.of(parcel));

        var result = service().findAssignedOn("cou_001", date, 0, 50);

        assertEquals(1, result.content().size());
        assertEquals("SWF-2026-000001", result.content().get(0).trackingNumber());
        assertEquals("Test Recipient", result.content().get(0).recipientName());
        assertEquals("Test Address", result.content().get(0).recipientAddress());
        assertEquals(0, result.page());
        assertEquals(50, result.size());
        assertEquals(false, result.hasNext());

        ArgumentCaptor<Instant> times = ArgumentCaptor.forClass(Instant.class);
        verify(assignmentRepository)
                .findByCourierIdAndAssignedAtGreaterThanEqualAndAssignedAtLessThanAndUnassignedAtIsNullOrderByAssignedAtAscIdAsc(
                        org.mockito.ArgumentMatchers.eq("cou_001"), times.capture(), times.capture(),
                        org.mockito.ArgumentMatchers.eq(PageRequest.of(0, 50)));
        assertEquals(expectedStart, times.getAllValues().get(0));
        assertEquals(expectedEnd, times.getAllValues().get(1));
    }

    @Test
    void assignedDateWithNoMatchesReturnsEmptyListWithoutParcelLookup() {
        var pageable = PageRequest.of(0, 50);
        when(assignmentRepository
                .findByCourierIdAndAssignedAtGreaterThanEqualAndAssignedAtLessThanAndUnassignedAtIsNullOrderByAssignedAtAscIdAsc(
                        any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));

        var result = service().findAssignedOn("cou_001", LocalDate.parse("2026-11-14"), 0, 50);
        assertEquals(List.of(), result.content());
        assertEquals(0, result.page());
        assertEquals(50, result.size());
        assertEquals(false, result.hasNext());
        verify(parcelRepository, never()).findByIdInAndCourierId(any(), any());
    }

    @Test
    void scheduledDateUsesExactIsoStringAndRequestedPage() {
        LocalDate date = LocalDate.parse("2026-11-14");
        var pageable = PageRequest.of(1, 20);
        when(parcelRepository.findByCourierIdAndEstimatedDeliveryDateOrderByTrackingNumberAsc(
                "cou_001", "2026-11-14", pageable))
                .thenReturn(new PageImpl<>(List.of(parcel("prc_001", "cou_001", "2026-11-14")), pageable, 41));

        var result = service().findScheduledFor("cou_001", date, 1, 20);

        assertEquals(1, result.content().size());
        assertEquals("2026-11-14", result.content().get(0).scheduledDate());
        assertEquals(1, result.page());
        assertEquals(20, result.size());
        assertEquals(true, result.hasNext());
        verify(parcelRepository).findByCourierIdAndEstimatedDeliveryDateOrderByTrackingNumberAsc(
                "cou_001", date.toString(), pageable);
    }

    @Test
    void historyUsesAscendingAssignedAtQueryAndUnknownParcelIs404() {
        when(parcelRepository.existsById("prc_001")).thenReturn(true);
        ParcelAssignment older = new ParcelAssignment("prc_001", "cou_old",
                Instant.parse("2026-11-13T00:00:00Z"), null, "Original");
        ParcelAssignment newer = new ParcelAssignment("prc_001", "cou_new",
                Instant.parse("2026-11-14T00:00:00Z"), null, "Reassigned");
        when(assignmentRepository.findByParcelIdOrderByAssignedAtAscIdAsc("prc_001"))
                .thenReturn(List.of(older, newer));

        var history = service().getAssignmentHistory("prc_001");

        assertEquals(List.of("cou_old", "cou_new"), history.stream()
                .map(item -> item.courierId()).toList());
        verify(assignmentRepository).findByParcelIdOrderByAssignedAtAscIdAsc("prc_001");

        when(parcelRepository.existsById("missing")).thenReturn(false);
        assertThrows(ParcelNotFoundException.class, () -> service().getAssignmentHistory("missing"));
    }

    @Test
    void assignedDateOverflowReturnsInvalidRequestWithoutQueryingRepositories() {
        assertThrows(InvalidRequestException.class,
                () -> service().findAssignedOn("cou_001", LocalDate.MAX, 0, 50));
        verify(assignmentRepository, never())
                .findByCourierIdAndAssignedAtGreaterThanEqualAndAssignedAtLessThanAndUnassignedAtIsNullOrderByAssignedAtAscIdAsc(
                        any(), any(), any(), any());
    }

    private Parcel parcel(String id, String courierId, String scheduledDate) {
        return new Parcel(id, courierId, "SWF-2026-000001", "in_transit", scheduledDate,
                "Test Recipient", "0700000000", "Test Address");
    }
}
