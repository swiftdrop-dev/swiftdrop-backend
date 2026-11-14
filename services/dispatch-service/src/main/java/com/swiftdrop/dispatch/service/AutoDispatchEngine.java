package com.swiftdrop.dispatch.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.dispatch.dto.response.CourierCandidateResponse;
import com.swiftdrop.dispatch.entity.DispatchAssignment;
import com.swiftdrop.dispatch.entity.DispatchJob;
import com.swiftdrop.dispatch.entity.enums.AssignmentDecision;
import com.swiftdrop.dispatch.entity.enums.DispatchStatus;
import com.swiftdrop.dispatch.entity.enums.DispatchType;
import com.swiftdrop.dispatch.repository.DispatchAssignmentRepository;
import com.swiftdrop.dispatch.repository.DispatchJobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class AutoDispatchEngine {

    private static final Logger log = LoggerFactory.getLogger(AutoDispatchEngine.class);
    private static final long OFFER_TTL_SECONDS = 60;

    private final DispatchJobRepository jobRepository;
    private final DispatchAssignmentRepository assignmentRepository;
    private final ProximityMatchingService proximityMatchingService;
    private final OutboxEventService outboxEventService;
    private final ObjectMapper objectMapper;

    public AutoDispatchEngine(DispatchJobRepository jobRepository,
                              DispatchAssignmentRepository assignmentRepository,
                              ProximityMatchingService proximityMatchingService,
                              OutboxEventService outboxEventService,
                              ObjectMapper objectMapper) {
        this.jobRepository = jobRepository;
        this.assignmentRepository = assignmentRepository;
        this.proximityMatchingService = proximityMatchingService;
        this.outboxEventService = outboxEventService;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelay = 15000) // Run every 15s
    @Transactional
    public void sweepExpiredOffersAndAutoDispatch() {
        sweepExpiredOffers();
        runAutoDispatchBatch();
    }

    @Transactional
    public void sweepExpiredOffers() {
        Instant now = Instant.now();
        List<DispatchJob> pendingAcceptance = jobRepository.findByStatusIn(
                List.of(DispatchStatus.ASSIGNED_PENDING_ACCEPTANCE),
                PageRequest.of(0, 50)
        ).getContent();

        for (DispatchJob job : pendingAcceptance) {
            if (job.getOfferExpiresAt() != null && job.getOfferExpiresAt().isBefore(now)) {
                log.info("Assignment offer expired for job {}", job.getId());
                job.setStatus(DispatchStatus.EXPIRED);

                DispatchAssignment assignment = assignmentRepository
                        .findFirstByDispatchJobIdAndDecisionOrderByAttemptNumberDesc(job.getId(), AssignmentDecision.OFFERED)
                        .orElse(null);

                if (assignment != null) {
                    assignment.setDecision(AssignmentDecision.EXPIRED);
                    assignment.setDecisionReason("Offer timed out after " + OFFER_TTL_SECONDS + " seconds");
                    assignment.setRespondedAt(now);
                    assignmentRepository.save(assignment);
                }

                jobRepository.save(job);
            }
        }
    }

    @Transactional
    public int runAutoDispatchBatch() {
        List<DispatchJob> unassignedJobs = jobRepository.findByStatusIn(
                Arrays.asList(DispatchStatus.PENDING_DISPATCH, DispatchStatus.DECLINED, DispatchStatus.EXPIRED),
                PageRequest.of(0, 20)
        ).getContent();

        int dispatchedCount = 0;
        for (DispatchJob job : unassignedJobs) {
            boolean assigned = autoAssignJob(job);
            if (assigned) {
                dispatchedCount++;
            }
        }
        return dispatchedCount;
    }

    @Transactional
    public boolean autoAssignJob(DispatchJob job) {
        // Collect previously declined/offered couriers for this job
        Set<UUID> excludedCourierIds = new HashSet<>();
        for (DispatchAssignment da : job.getAssignments()) {
            excludedCourierIds.add(da.getCourierId());
        }

        List<CourierCandidateResponse> candidates = proximityMatchingService.findBestCandidates(
                job.getPickupLatitude(),
                job.getPickupLongitude(),
                job.getRequiredVehicleType(),
                job.getParcelWeightKg(),
                excludedCourierIds
        );

        if (candidates.isEmpty()) {
            log.debug("No eligible couriers available for auto-dispatch of job {}", job.getId());
            return false;
        }

        CourierCandidateResponse bestCourier = candidates.get(0);
        Instant now = Instant.now();
        Instant expiresAt = now.plus(OFFER_TTL_SECONDS, ChronoUnit.SECONDS);

        job.setCourierId(bestCourier.getCourierId());
        job.setStatus(DispatchStatus.ASSIGNED_PENDING_ACCEPTANCE);
        job.setDispatchType(DispatchType.AUTOMATED);
        job.setAssignedAt(now);
        job.setOfferExpiresAt(expiresAt);
        job.setDispatcherNotes("Auto-dispatched via proximity matching engine. Distance: " + bestCourier.getDistanceKm() + " km");

        int nextAttempt = job.getAssignments().size() + 1;
        DispatchAssignment assignment = new DispatchAssignment();
        assignment.setCourierId(bestCourier.getCourierId());
        assignment.setAttemptNumber(nextAttempt);
        assignment.setDecision(AssignmentDecision.OFFERED);
        assignment.setOfferedDistanceKm(bestCourier.getDistanceKm());
        assignment.setOfferedAt(now);
        job.addAssignment(assignment);

        jobRepository.save(job);

        outboxEventService.publishEvent(
                "DispatchJob",
                job.getId().toString(),
                "DispatchJobAutoAssignedEvent",
                toJson(Map.of(
                        "jobId", job.getId(),
                        "parcelId", job.getParcelId(),
                        "courierId", bestCourier.getCourierId(),
                        "distanceKm", bestCourier.getDistanceKm(),
                        "offerExpiresAt", expiresAt.toString()
                ))
        );

        log.info("Auto-assigned job {} to courier {} (distance: {} km)",
                job.getId(), bestCourier.getCourierId(), bestCourier.getDistanceKm());
        return true;
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }
}
