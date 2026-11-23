package com.swiftdrop.parcel.controller;

import com.swiftdrop.parcel.dto.request.ParcelAssignmentRequest;
import com.swiftdrop.parcel.dto.response.ParcelAssignmentHistoryResponse;
import com.swiftdrop.parcel.dto.response.ParcelAssignmentResponse;
import com.swiftdrop.parcel.dto.response.ParcelDeliveryResponse;
import com.swiftdrop.parcel.dto.response.ParcelPageResponse;
import com.swiftdrop.parcel.exception.InvalidRequestException;
import com.swiftdrop.parcel.service.AssignmentOutcome;
import com.swiftdrop.parcel.service.ParcelAssignmentService;
import com.swiftdrop.parcel.service.ParcelQueryService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/internal/v1/parcels")
public class ParcelController {
    private static final int DEFAULT_PAGE_SIZE = 50;
    private static final int MAX_PAGE_SIZE = 100;

    private final ParcelAssignmentService assignmentService;
    private final ParcelQueryService parcelQueryService;

    public ParcelController(ParcelAssignmentService assignmentService, ParcelQueryService parcelQueryService) {
        this.assignmentService = assignmentService;
        this.parcelQueryService = parcelQueryService;
    }

    @PostMapping("/{parcelId}/assignments")
    public ResponseEntity<ParcelAssignmentResponse> assign(
            @PathVariable String parcelId,
            @Valid @RequestBody ParcelAssignmentRequest request) {
        AssignmentOutcome outcome = assignmentService.assign(parcelId, request.courierId(), null, request.reason());
        var assignment = outcome.assignment();
        var response = new ParcelAssignmentResponse(assignment.getId(), assignment.getParcelId(),
                assignment.getCourierId(), assignment.getAssignedAt(), assignment.getUnassignedAt());
        if (outcome.created()) {
            return ResponseEntity.status(201).body(response);
        }
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ParcelPageResponse<ParcelDeliveryResponse> findParcels(
            @RequestParam String courierId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate assignedDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate scheduledDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + DEFAULT_PAGE_SIZE) int size) {
        if (courierId.isBlank()) {
            throw new InvalidRequestException("courierId must not be blank");
        }
        if ((assignedDate == null) == (scheduledDate == null)) {
            throw new InvalidRequestException("Provide exactly one of assignedDate or scheduledDate");
        }
        if (page < 0) {
            throw new InvalidRequestException("page must be zero or greater");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new InvalidRequestException("size must be between 1 and " + MAX_PAGE_SIZE);
        }

        if (assignedDate != null) {
            return parcelQueryService.findAssignedOn(courierId, assignedDate, page, size);
        }
        return parcelQueryService.findScheduledFor(courierId, scheduledDate, page, size);
    }

    @GetMapping("/{parcelId}/assignment-history")
    public List<ParcelAssignmentHistoryResponse> getAssignmentHistory(@PathVariable String parcelId) {
        return parcelQueryService.getAssignmentHistory(parcelId);
    }
}
