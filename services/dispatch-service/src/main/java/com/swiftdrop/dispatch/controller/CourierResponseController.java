package com.swiftdrop.dispatch.controller;

import com.swiftdrop.dispatch.dto.ApiResponse;
import com.swiftdrop.dispatch.dto.request.CourierOfferDecisionRequest;
import com.swiftdrop.dispatch.dto.response.DispatchJobResponse;
import com.swiftdrop.dispatch.dto.response.DispatchJobSummaryResponse;
import com.swiftdrop.dispatch.security.SecurityUtils;
import com.swiftdrop.dispatch.service.DispatchJobService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/dispatch/courier/jobs")
public class CourierResponseController {

    private final DispatchJobService jobService;

    public CourierResponseController(DispatchJobService jobService) {
        this.jobService = jobService;
    }

    @GetMapping("/my-jobs")
    public ResponseEntity<ApiResponse<List<DispatchJobSummaryResponse>>> getMyJobs() {
        UUID courierId = SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new IllegalArgumentException("Courier ID is missing from security context or X-User-Id header"));
        List<DispatchJobSummaryResponse> jobs = jobService.getCourierJobs(courierId);
        return ResponseEntity.ok(ApiResponse.success(jobs));
    }

    @PostMapping("/{id}/decision")
    public ResponseEntity<ApiResponse<DispatchJobResponse>> submitDecision(
            @PathVariable("id") UUID id,
            @Valid @RequestBody CourierOfferDecisionRequest request) {
        UUID courierId = SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new IllegalArgumentException("Courier ID is missing from security context or X-User-Id header"));
        DispatchJobResponse response = jobService.handleCourierDecision(id, courierId, request);
        return ResponseEntity.ok(ApiResponse.success("Decision recorded", response));
    }
}
