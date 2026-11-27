package com.swiftdrop.dispatch.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.dispatch.client.CourierDirectoryClient;
import com.swiftdrop.dispatch.dto.request.CourierOfferDecisionRequest;
import com.swiftdrop.dispatch.dto.request.CreateDispatchJobRequest;
import com.swiftdrop.dispatch.dto.request.ManualAssignCourierRequest;
import com.swiftdrop.dispatch.dto.response.DispatchJobResponse;
import com.swiftdrop.dispatch.dto.response.DispatchJobSummaryResponse;
import com.swiftdrop.dispatch.entity.CourierStatusCache;
import com.swiftdrop.dispatch.entity.DispatchAssignment;
import com.swiftdrop.dispatch.entity.DispatchJob;
import com.swiftdrop.dispatch.entity.DispatchZone;
import com.swiftdrop.dispatch.entity.enums.AssignmentDecision;
import com.swiftdrop.dispatch.entity.enums.DispatchStatus;
import com.swiftdrop.dispatch.entity.enums.DispatchType;
import com.swiftdrop.dispatch.exception.AssignmentConflictException;
import com.swiftdrop.dispatch.exception.DuplicateResourceException;
import com.swiftdrop.dispatch.exception.InvalidDispatchStateException;
import com.swiftdrop.dispatch.exception.ResourceNotFoundException;
import com.swiftdrop.dispatch.mapper.DispatchMapper;
import com.swiftdrop.dispatch.repository.CourierStatusCacheRepository;
import com.swiftdrop.dispatch.repository.DispatchAssignmentRepository;
import com.swiftdrop.dispatch.repository.DispatchJobRepository;
import com.swiftdrop.dispatch.repository.DispatchZoneRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class DispatchJobService {

    private static final Logger log = LoggerFactory.getLogger(DispatchJobService.class);
    private static final long OFFER_TTL_SECONDS = 60; // 60s offer window
    private static final List<DispatchStatus> ACTIVE_ASSIGNMENTS = List.of(
            DispatchStatus.ASSIGNED_PENDING_ACCEPTANCE,
            DispatchStatus.ACCEPTED,
            DispatchStatus.IN_PROGRESS
    );

    private final DispatchJobRepository jobRepository;
    private final DispatchAssignmentRepository assignmentRepository;
    private final DispatchZoneRepository zoneRepository;
    private final CourierStatusCacheRepository courierCacheRepository;
    private final ProximityMatchingService proximityMatchingService;
    private final OutboxEventService outboxEventService;
    private final DispatchMapper dispatchMapper;
    private final ObjectMapper objectMapper;
    private final CourierDirectoryClient courierDirectoryClient;

    public DispatchJobService(DispatchJobRepository jobRepository,
                              DispatchAssignmentRepository assignmentRepository,
                              DispatchZoneRepository zoneRepository,
                              CourierStatusCacheRepository courierCacheRepository,
                              ProximityMatchingService proximityMatchingService,
                              OutboxEventService outboxEventService,
                              DispatchMapper dispatchMapper,
                              ObjectMapper objectMapper,
                              CourierDirectoryClient courierDirectoryClient) {
        this.jobRepository = jobRepository;
        this.assignmentRepository = assignmentRepository;
        this.zoneRepository = zoneRepository;
        this.courierCacheRepository = courierCacheRepository;
        this.proximityMatchingService = proximityMatchingService;
        this.outboxEventService = outboxEventService;
        this.dispatchMapper = dispatchMapper;
        this.objectMapper = objectMapper;
        this.courierDirectoryClient = courierDirectoryClient;
    }

    @Transactional
    public DispatchJobResponse createJob(CreateDispatchJobRequest request) {
        if (jobRepository.existsByParcelId(request.getParcelId())) {
            throw new DuplicateResourceException("Dispatch job already exists for parcel: " + request.getParcelId());
        }

        DispatchJob job = new DispatchJob();
        job.setParcelId(request.getParcelId());
        job.setTrackingNumber(request.getTrackingNumber());
        job.setMerchantId(request.getMerchantId());
        job.setPickupStreetAddress(request.getPickupStreetAddress());
        job.setPickupCity(request.getPickupCity());
        job.setPickupLatitude(request.getPickupLatitude());
        job.setPickupLongitude(request.getPickupLongitude());
        job.setDeliveryStreetAddress(request.getDeliveryStreetAddress());
        job.setDeliveryCity(request.getDeliveryCity());
        job.setDeliveryLatitude(request.getDeliveryLatitude());
        job.setDeliveryLongitude(request.getDeliveryLongitude());
        job.setParcelWeightKg(request.getParcelWeightKg());
        job.setTotalFee(request.getTotalFee());
        job.setRequiredVehicleType(request.getRequiredVehicleType());
        job.setPriority(request.getPriority());
        job.setDispatchType(DispatchType.MANUAL);
        job.setStatus(DispatchStatus.PENDING_DISPATCH);

        if (request.getZoneCode() != null) {
            zoneRepository.findByZoneCode(request.getZoneCode()).ifPresent(job::setZone);
        }

        DispatchJob saved = jobRepository.save(job);

        outboxEventService.publishEvent(
                "DispatchJob",
                saved.getId().toString(),
                "DispatchJobCreatedEvent",
                toJson(Map.of(
                        "jobId", saved.getId(),
                        "parcelId", saved.getParcelId(),
                        "trackingNumber", saved.getTrackingNumber(),
                        "status", saved.getStatus()
                ))
        );

        log.info("Created dispatch job [id={}, trackingNumber={}]", saved.getId(), saved.getTrackingNumber());
        return dispatchMapper.toJobResponse(saved);
    }

    @Transactional
    public DispatchJobResponse manualAssignCourier(UUID jobId, ManualAssignCourierRequest request, UUID dispatcherId) {
        DispatchJob job = jobRepository.findByIdForUpdate(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Dispatch job not found: " + jobId));

        if (job.getStatus() != DispatchStatus.PENDING_DISPATCH &&
                job.getStatus() != DispatchStatus.DECLINED &&
                job.getStatus() != DispatchStatus.EXPIRED) {
            throw new InvalidDispatchStateException("Cannot assign courier when job status is: " + job.getStatus());
        }

        String courierId = request.getCourierId();
        courierDirectoryClient.requireEligible(courierId);
        CourierStatusCache courier = courierCacheRepository.findByCourierIdForUpdate(courierId)
                .orElseThrow(() -> new InvalidDispatchStateException("Courier is not eligible for assignment"));
        if (!isAssignable(courier)) {
            throw new InvalidDispatchStateException("Courier is not available for assignment");
        }
        if (jobRepository.existsByCourierIdAndStatusInAndIdNot(courierId, ACTIVE_ASSIGNMENTS, jobId)) {
            throw new AssignmentConflictException("Courier already has an active offer or assignment");
        }
        Optional<CourierStatusCache> courierOpt = Optional.of(courier);
        BigDecimal offeredDistance = null;
        if (courierOpt.isPresent() && courierOpt.get().getCurrentLatitude() != null
                && job.getPickupLatitude() != null && job.getPickupLongitude() != null) {
            double distance = proximityMatchingService.calculateHaversineDistanceKm(
                    job.getPickupLatitude().doubleValue(), job.getPickupLongitude().doubleValue(),
                    courierOpt.get().getCurrentLatitude().doubleValue(), courierOpt.get().getCurrentLongitude().doubleValue()
            );
            offeredDistance = BigDecimal.valueOf(distance).setScale(2, RoundingMode.HALF_UP);
        }

        Instant now = Instant.now();
        Instant expiresAt = now.plus(OFFER_TTL_SECONDS, ChronoUnit.SECONDS);

        job.setCourierId(courierId);
        job.setAssignedBy(dispatcherId);
        job.setAssignedAt(now);
        job.setOfferExpiresAt(expiresAt);
        job.setStatus(DispatchStatus.ASSIGNED_PENDING_ACCEPTANCE);
        job.setDispatchType(DispatchType.MANUAL);
        job.setDispatcherNotes(request.getDispatcherNotes());

        int nextAttempt = job.getAssignments().size() + 1;
        DispatchAssignment assignment = new DispatchAssignment();
        assignment.setCourierId(courierId);
        assignment.setAttemptNumber(nextAttempt);
        assignment.setDecision(AssignmentDecision.OFFERED);
        assignment.setOfferedDistanceKm(offeredDistance);
        assignment.setOfferedAt(now);
        job.addAssignment(assignment);

        DispatchJob saved = jobRepository.save(job);

        outboxEventService.publishEvent(
                "DispatchJob",
                saved.getId().toString(),
                "DispatchJobAssignedEvent",
                toJson(Map.of(
                        "eventId", UUID.randomUUID().toString(),
                        "eventType", "DISPATCH_ASSIGNED",
                        "eventVersion", "1.0",
                        "occurredAt", now.toString(),
                        "jobId", saved.getId(),
                        "parcelId", saved.getParcelId(),
                        "courierId", courierId,
                        "trackingNumber", saved.getTrackingNumber(),
                        "offerExpiresAt", expiresAt.toString(),
                        "status", "ASSIGNED_PENDING_ACCEPTANCE"
                ))
        );

        log.info("Assigned dispatch job {} to courier {} (offer expires in {}s)", saved.getId(), courierId, OFFER_TTL_SECONDS);
        return dispatchMapper.toJobResponse(saved);
    }

    private boolean isAssignable(CourierStatusCache courier) {
        return Boolean.TRUE.equals(courier.getIsOnline()) && Boolean.TRUE.equals(courier.getIsAvailable());
    }

    @Transactional
    public DispatchJobResponse handleCourierDecision(UUID jobId, String courierId, CourierOfferDecisionRequest request) {
        DispatchJob job = getJobEntity(jobId);

        // Idempotency check: if courier re-submits matching decision, safely return current state
        if (Boolean.TRUE.equals(request.getAccept()) && job.getStatus() == DispatchStatus.ACCEPTED
                && courierId.equals(job.getCourierId())) {
            log.info("Dispatch job {} already accepted by courier {}, returning existing job", jobId, courierId);
            return dispatchMapper.toJobResponse(job);
        }
        if (Boolean.FALSE.equals(request.getAccept()) && job.getStatus() == DispatchStatus.DECLINED) {
            log.info("Dispatch job {} already declined, returning existing job", jobId);
            return dispatchMapper.toJobResponse(job);
        }

        if (job.getStatus() != DispatchStatus.ASSIGNED_PENDING_ACCEPTANCE) {
            throw new InvalidDispatchStateException("Job is not awaiting courier decision. Current status: " + job.getStatus());
        }

        if (job.getCourierId() == null || !job.getCourierId().equals(courierId)) {
            throw new InvalidDispatchStateException("Courier " + courierId + " is not the assigned courier for job " + jobId);
        }

        Instant now = Instant.now();
        DispatchAssignment currentAssignment = assignmentRepository
                .findFirstByDispatchJobIdAndDecisionOrderByAttemptNumberDesc(jobId, AssignmentDecision.OFFERED)
                .orElse(null);

        if (Boolean.TRUE.equals(request.getAccept())) {
            // Check offer timeout
            if (job.getOfferExpiresAt() != null && job.getOfferExpiresAt().isBefore(now)) {
                job.setStatus(DispatchStatus.EXPIRED);
                if (currentAssignment != null) {
                    currentAssignment.setDecision(AssignmentDecision.EXPIRED);
                    currentAssignment.setRespondedAt(now);
                    currentAssignment.setDecisionReason("Offer acceptance window expired");
                    assignmentRepository.save(currentAssignment);
                }
                jobRepository.save(job);
                throw new InvalidDispatchStateException("Assignment offer has expired");
            }

            job.setStatus(DispatchStatus.ACCEPTED);
            job.setAcceptedAt(now);

            if (currentAssignment != null) {
                currentAssignment.setDecision(AssignmentDecision.ACCEPTED);
                currentAssignment.setRespondedAt(now);
                assignmentRepository.save(currentAssignment);
            }

            // Update courier cache active deliveries count
            courierCacheRepository.findById(courierId).ifPresent(c -> {
                c.setActiveDeliveriesCount(c.getActiveDeliveriesCount() + 1);
                courierCacheRepository.save(c);
            });

            DispatchJob saved = jobRepository.save(job);

            outboxEventService.publishEvent(
                    "DispatchJob",
                    saved.getId().toString(),
                    "DispatchJobAcceptedEvent",
                    toJson(Map.of(
                            "eventId", UUID.randomUUID().toString(),
                            "eventType", "COURIER_ACCEPTED",
                            "eventVersion", "1.0",
                            "occurredAt", now.toString(),
                            "jobId", saved.getId(),
                            "parcelId", saved.getParcelId(),
                            "courierId", courierId,
                            "trackingNumber", saved.getTrackingNumber(),
                            "status", "ASSIGNED"
                    ))
            );

            log.info("Courier {} accepted dispatch job {}", courierId, jobId);
            return dispatchMapper.toJobResponse(saved);
        } else {
            // Courier declined
            job.setStatus(DispatchStatus.DECLINED);

            if (currentAssignment != null) {
                currentAssignment.setDecision(AssignmentDecision.DECLINED);
                currentAssignment.setDecisionReason(request.getReason() != null ? request.getReason() : "Declined by courier");
                currentAssignment.setRespondedAt(now);
                assignmentRepository.save(currentAssignment);
            }

            DispatchJob saved = jobRepository.save(job);

            outboxEventService.publishEvent(
                    "DispatchJob",
                    saved.getId().toString(),
                    "DispatchJobDeclinedEvent",
                    toJson(Map.of(
                            "eventId", UUID.randomUUID().toString(),
                            "eventType", "COURIER_DECLINED",
                            "eventVersion", "1.0",
                            "occurredAt", now.toString(),
                            "jobId", saved.getId(),
                            "parcelId", saved.getParcelId(),
                            "courierId", courierId,
                            "trackingNumber", saved.getTrackingNumber(),
                            "status", "DECLINED",
                            "reason", request.getReason() != null ? request.getReason() : ""
                    ))
            );

            log.info("Courier {} declined dispatch job {}: {}", courierId, jobId, request.getReason());
            return dispatchMapper.toJobResponse(saved);
        }
    }

    @Transactional
    public DispatchJobResponse cancelJob(UUID jobId, String reason, UUID cancelledBy) {
        DispatchJob job = getJobEntity(jobId);

        if (job.getStatus() == DispatchStatus.COMPLETED || job.getStatus() == DispatchStatus.CANCELLED) {
            throw new InvalidDispatchStateException("Cannot cancel job in status: " + job.getStatus());
        }

        job.setStatus(DispatchStatus.CANCELLED);
        job.setCancelledAt(Instant.now());
        job.setCancellationReason(reason != null ? reason : "Cancelled by dispatcher");

        DispatchJob saved = jobRepository.save(job);

        outboxEventService.publishEvent(
                "DispatchJob",
                saved.getId().toString(),
                "DispatchJobCancelledEvent",
                toJson(Map.of(
                        "jobId", saved.getId(),
                        "parcelId", saved.getParcelId(),
                        "reason", saved.getCancellationReason()
                ))
        );

        log.info("Cancelled dispatch job {}: {}", jobId, reason);
        return dispatchMapper.toJobResponse(saved);
    }

    @Transactional(readOnly = true)
    public DispatchJobResponse getJobById(UUID jobId) {
        return dispatchMapper.toJobResponse(getJobEntity(jobId));
    }

    @Transactional(readOnly = true)
    public DispatchJobResponse getJobByTrackingNumber(String trackingNumber) {
        DispatchJob job = jobRepository.findByTrackingNumber(trackingNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Dispatch job not found with tracking number: " + trackingNumber));
        return dispatchMapper.toJobResponse(job);
    }

    @Transactional(readOnly = true)
    public Page<DispatchJobSummaryResponse> getJobsByStatus(DispatchStatus status, Pageable pageable) {
        if (status != null) {
            return jobRepository.findByStatus(status, pageable).map(dispatchMapper::toJobSummaryResponse);
        }
        return jobRepository.findAll(pageable).map(dispatchMapper::toJobSummaryResponse);
    }

    @Transactional(readOnly = true)
    public Page<DispatchJobSummaryResponse> getUnassignedJobs(String zoneCode, Pageable pageable) {
        String zone = zoneCode == null || zoneCode.isBlank() ? null : zoneCode.trim();
        return jobRepository.findUnassignedByZone(
                Arrays.asList(DispatchStatus.PENDING_DISPATCH, DispatchStatus.DECLINED, DispatchStatus.EXPIRED),
                zone,
                pageable
        ).map(dispatchMapper::toJobSummaryResponse);
    }

    @Transactional(readOnly = true)
    public List<DispatchJobSummaryResponse> getActiveAssignments() {
        List<DispatchJobSummaryResponse> summaries = jobRepository.findByStatusIn(ACTIVE_ASSIGNMENTS).stream()
                .map(dispatchMapper::toJobSummaryResponse)
                .collect(Collectors.toList());
        Set<String> courierIds = summaries.stream()
                .map(DispatchJobSummaryResponse::getCourierId)
                .filter(id -> id != null && !id.isBlank())
                .collect(Collectors.toSet());
        if (courierIds.isEmpty()) {
            return summaries;
        }
        Map<String, CourierStatusCache> locations = courierCacheRepository.findAllById(courierIds).stream()
                .collect(Collectors.toMap(CourierStatusCache::getCourierId, cache -> cache));
        Instant freshAfter = Instant.now().minus(15, ChronoUnit.MINUTES);
        for (DispatchJobSummaryResponse summary : summaries) {
            CourierStatusCache cache = locations.get(summary.getCourierId());
            if (cache == null || cache.getCurrentLatitude() == null || cache.getCurrentLongitude() == null) {
                continue;
            }
            Instant updatedAt = cache.getLastLocationUpdatedAt();
            if (updatedAt != null && updatedAt.isBefore(freshAfter)) {
                continue;
            }
            summary.setCourierLatitude(cache.getCurrentLatitude());
            summary.setCourierLongitude(cache.getCurrentLongitude());
        }
        return summaries;
    }

    @Transactional(readOnly = true)
    public List<DispatchJobSummaryResponse> getCourierJobs(String courierId) {
        return jobRepository.findByCourierIdAndStatusIn(
                courierId,
                Arrays.asList(DispatchStatus.ASSIGNED_PENDING_ACCEPTANCE, DispatchStatus.ACCEPTED, DispatchStatus.IN_PROGRESS)
        ).stream().map(dispatchMapper::toJobSummaryResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Map<String, Long> getDashboardSummary() {
        Map<String, Long> summary = new HashMap<>();
        summary.put("pendingDispatch", jobRepository.countByStatus(DispatchStatus.PENDING_DISPATCH));
        summary.put("assignedPendingAcceptance", jobRepository.countByStatus(DispatchStatus.ASSIGNED_PENDING_ACCEPTANCE));
        summary.put("accepted", jobRepository.countByStatus(DispatchStatus.ACCEPTED));
        summary.put("inProgress", jobRepository.countByStatus(DispatchStatus.IN_PROGRESS));
        summary.put("completed", jobRepository.countByStatus(DispatchStatus.COMPLETED));
        summary.put("declined", jobRepository.countByStatus(DispatchStatus.DECLINED));
        summary.put("expired", jobRepository.countByStatus(DispatchStatus.EXPIRED));
        return summary;
    }

    public DispatchJob getJobEntity(UUID jobId) {
        return jobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Dispatch job not found: " + jobId));
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }
}
