package com.swiftdrop.dispatch.controller;

import com.swiftdrop.dispatch.dto.ApiResponse;
import com.swiftdrop.dispatch.dto.request.CreateDispatchJobRequest;
import com.swiftdrop.dispatch.dto.request.ManualAssignCourierRequest;
import com.swiftdrop.dispatch.dto.response.CourierCandidateResponse;
import com.swiftdrop.dispatch.dto.response.DispatchJobResponse;
import com.swiftdrop.dispatch.dto.response.DispatchJobSummaryResponse;
import com.swiftdrop.dispatch.entity.DispatchJob;
import com.swiftdrop.dispatch.entity.enums.DispatchStatus;
import com.swiftdrop.dispatch.security.SecurityUtils;
import com.swiftdrop.dispatch.service.AutoDispatchEngine;
import com.swiftdrop.dispatch.service.DispatchJobService;
import com.swiftdrop.dispatch.service.ProximityMatchingService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/dispatch/jobs")
public class DispatchJobController {

    private final DispatchJobService jobService;
    private final ProximityMatchingService proximityMatchingService;
    private final AutoDispatchEngine autoDispatchEngine;

    public DispatchJobController(DispatchJobService jobService,
                                 ProximityMatchingService proximityMatchingService,
                                 AutoDispatchEngine autoDispatchEngine) {
        this.jobService = jobService;
        this.proximityMatchingService = proximityMatchingService;
        this.autoDispatchEngine = autoDispatchEngine;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<DispatchJobResponse>> createJob(
            @Valid @RequestBody CreateDispatchJobRequest request) {
        DispatchJobResponse response = jobService.createJob(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Dispatch job created in PENDING_DISPATCH", response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<DispatchJobSummaryResponse>>> getJobs(
            @RequestParam(value = "status", required = false) DispatchStatus status,
            @PageableDefault(size = 20) Pageable pageable) {
        Page<DispatchJobSummaryResponse> page = jobService.getJobsByStatus(status, pageable);
        return ResponseEntity.ok(ApiResponse.success(page));
    }

    @GetMapping("/unassigned")
    public ResponseEntity<ApiResponse<Page<DispatchJobSummaryResponse>>> getUnassignedJobs(
            @PageableDefault(size = 20) Pageable pageable) {
        Page<DispatchJobSummaryResponse> page = jobService.getUnassignedJobs(pageable);
        return ResponseEntity.ok(ApiResponse.success(page));
    }

    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<Map<String, Long>>> getSummary() {
        Map<String, Long> summary = jobService.getDashboardSummary();
        return ResponseEntity.ok(ApiResponse.success(summary));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<DispatchJobResponse>> getJobById(@PathVariable("id") UUID id) {
        DispatchJobResponse response = jobService.getJobById(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/tracking/{trackingNumber}")
    public ResponseEntity<ApiResponse<DispatchJobResponse>> getJobByTracking(
            @PathVariable("trackingNumber") String trackingNumber) {
        DispatchJobResponse response = jobService.getJobByTrackingNumber(trackingNumber);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/{id}/candidates")
    public ResponseEntity<ApiResponse<List<CourierCandidateResponse>>> getCandidates(@PathVariable("id") UUID id) {
        DispatchJob job = jobService.getJobEntity(id);
        List<CourierCandidateResponse> candidates = proximityMatchingService.findBestCandidates(
                job.getPickupLatitude(),
                job.getPickupLongitude(),
                job.getRequiredVehicleType(),
                job.getParcelWeightKg(),
                job.getAssignments().stream().map(a -> a.getCourierId()).collect(Collectors.toSet())
        );
        return ResponseEntity.ok(ApiResponse.success(candidates));
    }

    @PostMapping("/{id}/assign")
    public ResponseEntity<ApiResponse<DispatchJobResponse>> manualAssign(
            @PathVariable("id") UUID id,
            @Valid @RequestBody ManualAssignCourierRequest request) {
        UUID dispatcherId = SecurityUtils.getCurrentUserId()
                .orElse(UUID.fromString("00000000-0000-0000-0000-000000000002"));
        DispatchJobResponse response = jobService.manualAssignCourier(id, request, dispatcherId);
        return ResponseEntity.ok(ApiResponse.success("Courier offered assignment with 60-second window", response));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<DispatchJobResponse>> cancelJob(
            @PathVariable("id") UUID id,
            @RequestParam(value = "reason", required = false) String reason) {
        UUID dispatcherId = SecurityUtils.getCurrentUserId()
                .orElse(UUID.fromString("00000000-0000-0000-0000-000000000002"));
        DispatchJobResponse response = jobService.cancelJob(id, reason, dispatcherId);
        return ResponseEntity.ok(ApiResponse.success("Dispatch job cancelled", response));
    }

    @PostMapping("/auto-dispatch")
    public ResponseEntity<ApiResponse<Integer>> triggerAutoDispatch() {
        int count = autoDispatchEngine.runAutoDispatchBatch();
        return ResponseEntity.ok(ApiResponse.success("Auto-dispatch batch executed", count));
    }
}
