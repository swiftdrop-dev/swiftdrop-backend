package com.swiftdrop.dispatch.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.dispatch.dto.response.CourierCandidateResponse;
import com.swiftdrop.dispatch.entity.DispatchAssignment;
import com.swiftdrop.dispatch.entity.DispatchJob;
import com.swiftdrop.dispatch.entity.enums.AssignmentDecision;
import com.swiftdrop.dispatch.entity.enums.DispatchStatus;
import com.swiftdrop.dispatch.entity.enums.VehicleType;
import com.swiftdrop.dispatch.repository.DispatchAssignmentRepository;
import com.swiftdrop.dispatch.repository.DispatchJobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AutoDispatchEngineTest {

    @Mock
    private DispatchJobRepository jobRepository;

    @Mock
    private DispatchAssignmentRepository assignmentRepository;

    @Mock
    private ProximityMatchingService proximityMatchingService;

    @Mock
    private OutboxEventService outboxEventService;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private AutoDispatchEngine autoDispatchEngine;

    private DispatchJob job;
    private UUID jobId;

    @BeforeEach
    void setUp() {
        jobId = UUID.randomUUID();
        job = new DispatchJob();
        job.setId(jobId);
        job.setParcelId(UUID.randomUUID());
        job.setTrackingNumber("SD-AUTO-1");
        job.setPickupLatitude(new BigDecimal("6.9271"));
        job.setPickupLongitude(new BigDecimal("79.8612"));
        job.setRequiredVehicleType(VehicleType.MOTORCYCLE);
        job.setParcelWeightKg(new BigDecimal("2.00"));
        job.setStatus(DispatchStatus.PENDING_DISPATCH);
    }

    @Test
    @DisplayName("Should sweep and mark expired offers as EXPIRED")
    void testSweepExpiredOffers() {
        job.setStatus(DispatchStatus.ASSIGNED_PENDING_ACCEPTANCE);
        job.setOfferExpiresAt(Instant.now().minus(10, ChronoUnit.SECONDS)); // Expired 10s ago

        DispatchAssignment assignment = new DispatchAssignment();
        assignment.setDecision(AssignmentDecision.OFFERED);

        when(jobRepository.findByStatusIn(any(), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(job)));
        when(assignmentRepository.findFirstByDispatchJobIdAndDecisionOrderByAttemptNumberDesc(jobId, AssignmentDecision.OFFERED))
                .thenReturn(Optional.of(assignment));

        autoDispatchEngine.sweepExpiredOffers();

        assertThat(job.getStatus()).isEqualTo(DispatchStatus.EXPIRED);
        assertThat(assignment.getDecision()).isEqualTo(AssignmentDecision.EXPIRED);
        verify(jobRepository).save(job);
    }

    @Test
    @DisplayName("Should auto-assign best candidate courier for unassigned job")
    void testAutoAssignJob_Success() {
        UUID courierId = UUID.randomUUID();
        CourierCandidateResponse candidate = new CourierCandidateResponse(
                courierId, "John Courier", "+94771234567", VehicleType.MOTORCYCLE,
                new BigDecimal("2.50"), 0, 4, 1.5
        );

        when(proximityMatchingService.findBestCandidates(any(), any(), any(), any(), any()))
                .thenReturn(List.of(candidate));
        when(jobRepository.save(any(DispatchJob.class))).thenAnswer(i -> i.getArgument(0));

        boolean assigned = autoDispatchEngine.autoAssignJob(job);

        assertThat(assigned).isTrue();
        assertThat(job.getStatus()).isEqualTo(DispatchStatus.ASSIGNED_PENDING_ACCEPTANCE);
        assertThat(job.getCourierId()).isEqualTo(courierId);
        assertThat(job.getOfferExpiresAt()).isAfter(Instant.now());
        verify(outboxEventService).publishEvent(eq("DispatchJob"), eq(jobId.toString()), eq("DispatchJobAutoAssignedEvent"), any());
    }

    @Test
    @DisplayName("Should not assign when no candidates are found")
    void testAutoAssignJob_NoCandidates() {
        when(proximityMatchingService.findBestCandidates(any(), any(), any(), any(), any()))
                .thenReturn(Collections.emptyList());

        boolean assigned = autoDispatchEngine.autoAssignJob(job);

        assertThat(assigned).isFalse();
        assertThat(job.getStatus()).isEqualTo(DispatchStatus.PENDING_DISPATCH);
    }
}
