package com.swiftdrop.dispatch.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.dispatch.dto.request.CourierOfferDecisionRequest;
import com.swiftdrop.dispatch.dto.request.CreateDispatchJobRequest;
import com.swiftdrop.dispatch.dto.request.ManualAssignCourierRequest;
import com.swiftdrop.dispatch.dto.response.DispatchJobResponse;
import com.swiftdrop.dispatch.entity.CourierStatusCache;
import com.swiftdrop.dispatch.entity.DispatchAssignment;
import com.swiftdrop.dispatch.entity.DispatchJob;
import com.swiftdrop.dispatch.entity.enums.AssignmentDecision;
import com.swiftdrop.dispatch.entity.enums.DispatchPriority;
import com.swiftdrop.dispatch.entity.enums.DispatchStatus;
import com.swiftdrop.dispatch.entity.enums.VehicleType;
import com.swiftdrop.dispatch.mapper.DispatchMapper;
import com.swiftdrop.dispatch.repository.CourierStatusCacheRepository;
import com.swiftdrop.dispatch.repository.DispatchAssignmentRepository;
import com.swiftdrop.dispatch.repository.DispatchJobRepository;
import com.swiftdrop.dispatch.repository.DispatchZoneRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DispatchJobServiceTest {

    @Mock
    private DispatchJobRepository jobRepository;

    @Mock
    private DispatchAssignmentRepository assignmentRepository;

    @Mock
    private DispatchZoneRepository zoneRepository;

    @Mock
    private CourierStatusCacheRepository courierCacheRepository;

    @Mock
    private ProximityMatchingService proximityMatchingService;

    @Mock
    private OutboxEventService outboxEventService;

    @Spy
    private DispatchMapper dispatchMapper = new DispatchMapper();

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private DispatchJobService jobService;

    private UUID jobId;
    private UUID parcelId;
    private UUID courierId;
    private UUID dispatcherId;

    @BeforeEach
    void setUp() {
        jobId = UUID.randomUUID();
        parcelId = UUID.randomUUID();
        courierId = UUID.randomUUID();
        dispatcherId = UUID.randomUUID();
    }

    @Test
    @DisplayName("Should successfully create dispatch job in PENDING_DISPATCH status")
    void testCreateJob_Success() {
        CreateDispatchJobRequest request = new CreateDispatchJobRequest();
        request.setParcelId(parcelId);
        request.setTrackingNumber("SD-2026-9999");
        request.setMerchantId(UUID.randomUUID());
        request.setPickupStreetAddress("123 Main St");
        request.setPickupCity("Colombo");
        request.setPickupLatitude(new BigDecimal("6.9271"));
        request.setPickupLongitude(new BigDecimal("79.8612"));
        request.setDeliveryStreetAddress("456 Galle Rd");
        request.setDeliveryCity("Colombo");
        request.setDeliveryLatitude(new BigDecimal("6.9100"));
        request.setDeliveryLongitude(new BigDecimal("79.8500"));
        request.setParcelWeightKg(new BigDecimal("1.50"));
        request.setRequiredVehicleType(VehicleType.MOTORCYCLE);
        request.setPriority(DispatchPriority.STANDARD);

        when(jobRepository.existsByParcelId(parcelId)).thenReturn(false);
        when(jobRepository.save(any(DispatchJob.class))).thenAnswer(i -> {
            DispatchJob j = i.getArgument(0);
            j.setId(jobId);
            return j;
        });

        DispatchJobResponse response = jobService.createJob(request);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(jobId);
        assertThat(response.getStatus()).isEqualTo(DispatchStatus.PENDING_DISPATCH);
        verify(outboxEventService).publishEvent(eq("DispatchJob"), eq(jobId.toString()), eq("DispatchJobCreatedEvent"), any());
    }

    @Test
    @DisplayName("Should manually assign courier and set 60s offer expiration")
    void testManualAssign_Success() {
        DispatchJob job = new DispatchJob();
        job.setId(jobId);
        job.setParcelId(parcelId);
        job.setTrackingNumber("SD-123");
        job.setPickupLatitude(new BigDecimal("6.9271"));
        job.setPickupLongitude(new BigDecimal("79.8612"));
        job.setStatus(DispatchStatus.PENDING_DISPATCH);

        when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(courierCacheRepository.findById(courierId)).thenReturn(Optional.of(new CourierStatusCache()));
        when(jobRepository.save(any(DispatchJob.class))).thenAnswer(i -> i.getArgument(0));

        ManualAssignCourierRequest request = new ManualAssignCourierRequest(courierId, "Urgent pickup request");

        DispatchJobResponse response = jobService.manualAssignCourier(jobId, request, dispatcherId);

        assertThat(response.getStatus()).isEqualTo(DispatchStatus.ASSIGNED_PENDING_ACCEPTANCE);
        assertThat(response.getCourierId()).isEqualTo(courierId);
        assertThat(response.getOfferExpiresAt()).isAfter(Instant.now());
        verify(outboxEventService).publishEvent(eq("DispatchJob"), eq(jobId.toString()), eq("DispatchJobAssignedEvent"), any());
    }

    @Test
    @DisplayName("Should accept offer within TTL and update courier active deliveries count")
    void testHandleCourierDecision_Accept() {
        DispatchJob job = new DispatchJob();
        job.setId(jobId);
        job.setParcelId(parcelId);
        job.setTrackingNumber("SD-123");
        job.setCourierId(courierId);
        job.setStatus(DispatchStatus.ASSIGNED_PENDING_ACCEPTANCE);
        job.setOfferExpiresAt(Instant.now().plus(60, ChronoUnit.SECONDS));

        DispatchAssignment assignment = new DispatchAssignment();
        assignment.setCourierId(courierId);
        assignment.setDecision(AssignmentDecision.OFFERED);

        CourierStatusCache courier = new CourierStatusCache();
        courier.setCourierId(courierId);
        courier.setActiveDeliveriesCount(0);

        when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(assignmentRepository.findFirstByDispatchJobIdAndDecisionOrderByAttemptNumberDesc(jobId, AssignmentDecision.OFFERED))
                .thenReturn(Optional.of(assignment));
        when(courierCacheRepository.findById(courierId)).thenReturn(Optional.of(courier));
        when(jobRepository.save(any(DispatchJob.class))).thenAnswer(i -> i.getArgument(0));

        CourierOfferDecisionRequest decisionRequest = new CourierOfferDecisionRequest(true, null);
        DispatchJobResponse response = jobService.handleCourierDecision(jobId, courierId, decisionRequest);

        assertThat(response.getStatus()).isEqualTo(DispatchStatus.ACCEPTED);
        assertThat(courier.getActiveDeliveriesCount()).isEqualTo(1);
        verify(outboxEventService).publishEvent(eq("DispatchJob"), eq(jobId.toString()), eq("DispatchJobAcceptedEvent"), any());
    }

    @Test
    @DisplayName("Should decline offer and transition to DECLINED")
    void testHandleCourierDecision_Decline() {
        DispatchJob job = new DispatchJob();
        job.setId(jobId);
        job.setParcelId(parcelId);
        job.setTrackingNumber("SD-123");
        job.setCourierId(courierId);
        job.setStatus(DispatchStatus.ASSIGNED_PENDING_ACCEPTANCE);
        job.setOfferExpiresAt(Instant.now().plus(60, ChronoUnit.SECONDS));

        DispatchAssignment assignment = new DispatchAssignment();
        assignment.setCourierId(courierId);
        assignment.setDecision(AssignmentDecision.OFFERED);

        when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(assignmentRepository.findFirstByDispatchJobIdAndDecisionOrderByAttemptNumberDesc(jobId, AssignmentDecision.OFFERED))
                .thenReturn(Optional.of(assignment));
        when(jobRepository.save(any(DispatchJob.class))).thenAnswer(i -> i.getArgument(0));

        CourierOfferDecisionRequest decisionRequest = new CourierOfferDecisionRequest(false, "Traffic congestion");
        DispatchJobResponse response = jobService.handleCourierDecision(jobId, courierId, decisionRequest);

        assertThat(response.getStatus()).isEqualTo(DispatchStatus.DECLINED);
        verify(outboxEventService).publishEvent(eq("DispatchJob"), eq(jobId.toString()), eq("DispatchJobDeclinedEvent"), any());
    }
}
