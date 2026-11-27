package com.swiftdrop.parcel.controller;

import com.swiftdrop.parcel.dto.request.DeliveryCompletionRequest;
import com.swiftdrop.parcel.dto.request.OtpVerificationRequest;
import com.swiftdrop.parcel.dto.request.ParcelStatusUpdateRequest;
import com.swiftdrop.parcel.dto.response.AssignedDelivery;
import com.swiftdrop.parcel.dto.response.DeliveryCompletionResponse;
import com.swiftdrop.parcel.dto.response.DeliveryPhotoProofResponse;
import com.swiftdrop.parcel.dto.response.OtpVerificationResponse;
import com.swiftdrop.parcel.dto.response.ParcelPageResponse;
import com.swiftdrop.parcel.dto.response.ParcelStatusUpdateResponse;
import com.swiftdrop.parcel.entity.Parcel;
import com.swiftdrop.parcel.entity.ParcelAssignment;
import com.swiftdrop.parcel.entity.ParcelStatusHistory;
import com.swiftdrop.parcel.exception.CourierAssignmentMismatchException;
import com.swiftdrop.parcel.exception.InvalidRequestException;
import com.swiftdrop.parcel.otp.OtpChallengeReceipt;
import com.swiftdrop.parcel.otp.OtpChallengeService;
import com.swiftdrop.parcel.otp.OtpVerificationResult;
import com.swiftdrop.parcel.photo.DeliveryPhotoProofReceipt;
import com.swiftdrop.parcel.photo.DeliveryPhotoProofService;
import com.swiftdrop.parcel.repository.ParcelAssignmentRepository;
import com.swiftdrop.parcel.security.CurrentUser;
import com.swiftdrop.parcel.security.UserPrincipal;
import com.swiftdrop.parcel.repository.ParcelRepository;
import com.swiftdrop.parcel.service.DeliveryCompletionResult;
import com.swiftdrop.parcel.service.DeliveryCompletionService;
import com.swiftdrop.parcel.service.ParcelStatusService;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/parcels")
public class ParcelDeliveryController {

    private final ParcelRepository parcelRepository;
    private final ParcelAssignmentRepository assignmentRepository;
    private final OtpChallengeService otpChallengeService;
    private final DeliveryPhotoProofService photoProofService;
    private final DeliveryCompletionService completionService;
    private final ParcelStatusService statusService;
    private final com.swiftdrop.parcel.repository.ParcelStatusHistoryRepository historyRepository;

    @org.springframework.beans.factory.annotation.Autowired
    public ParcelDeliveryController(ParcelRepository parcelRepository,
                                    ParcelAssignmentRepository assignmentRepository,
                                    OtpChallengeService otpChallengeService,
                                    DeliveryPhotoProofService photoProofService,
                                    DeliveryCompletionService completionService,
                                    ParcelStatusService statusService,
                                    com.swiftdrop.parcel.repository.ParcelStatusHistoryRepository historyRepository) {
        this.parcelRepository = parcelRepository;
        this.assignmentRepository = assignmentRepository;
        this.otpChallengeService = otpChallengeService;
        this.photoProofService = photoProofService;
        this.completionService = completionService;
        this.statusService = statusService;
        this.historyRepository = historyRepository;
    }

    public ParcelDeliveryController(ParcelRepository parcelRepository,
                                    ParcelAssignmentRepository assignmentRepository,
                                    OtpChallengeService otpChallengeService,
                                    DeliveryPhotoProofService photoProofService,
                                    DeliveryCompletionService completionService,
                                    ParcelStatusService statusService) {
        this(parcelRepository, assignmentRepository, otpChallengeService, photoProofService, completionService, statusService, null);
    }

    @GetMapping("/me/assigned-deliveries")
    public ResponseEntity<ParcelPageResponse<AssignedDelivery>> getAssignedDeliveries(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        if (page < 0) {
            throw new InvalidRequestException("Page index must not be negative");
        }
        if (size < 1 || size > 100) {
            throw new InvalidRequestException("Page size must be between 1 and 100");
        }

        String trustedCourierId = requireCourierId();
        var pageable = PageRequest.of(page, size);
        var assignmentPage = assignmentRepository.findByCourierIdAndUnassignedAtIsNullOrderByAssignedAtDesc(
                trustedCourierId, pageable);

        var assignments = assignmentPage.getContent();
        if (assignments.isEmpty()) {
            return ResponseEntity.ok(new ParcelPageResponse<>(List.of(), page, size, assignmentPage.hasNext()));
        }

        var parcelIds = assignments.stream().map(ParcelAssignment::getParcelId).toList();
        Map<String, Parcel> parcelsById = parcelRepository.findByIdInAndCourierId(parcelIds, trustedCourierId).stream()
                .collect(Collectors.toMap(Parcel::getId, Function.identity()));

        List<AssignedDelivery> content = assignments.stream().map(a -> {
            Parcel p = parcelsById.get(a.getParcelId());
            return new AssignedDelivery(
                    a.getParcelId(),
                    a.getId(),
                    p != null ? p.getTrackingNumber() : "",
                    p != null ? p.getStatus() : "",
                    p != null ? p.getEstimatedDeliveryDate() : null,
                    a.getAssignedAt(),
                    p != null ? p.getRecipientName() : null,
                    p != null ? p.getRecipientPhone() : null,
                    p != null ? p.getRecipientAddress() : null
            );
        }).toList();

        return ResponseEntity.ok(new ParcelPageResponse<>(content, page, size, assignmentPage.hasNext()));
    }

    @PostMapping("/{id}/delivery-otp")
    public ResponseEntity<OtpChallengeReceipt> requestDeliveryOtp(@PathVariable String id) {
        String trustedCourierId = requireCourierId();
        OtpChallengeReceipt receipt = otpChallengeService.issue(id, trustedCourierId);
        return ResponseEntity.ok(receipt);
    }

    @PostMapping("/{id}/delivery-otp/verify")
    public ResponseEntity<OtpVerificationResponse> verifyDeliveryOtp(
            @PathVariable String id,
            @Valid @RequestBody OtpVerificationRequest request) {

        String trustedCourierId = requireCourierId();
        OtpVerificationResult result = otpChallengeService.verify(
                id, request.challengeId(), trustedCourierId, request.code().toCharArray());
        return ResponseEntity.ok(new OtpVerificationResponse(
                result.challengeId(), result.outcome(), result.verifiedAt()));
    }

    @PostMapping(value = "/{id}/delivery-photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DeliveryPhotoProofResponse> uploadDeliveryPhoto(
            @PathVariable String id,
            @RequestParam("assignmentId") Long assignmentId,
            @RequestParam("photo") MultipartFile photo) throws IOException {

        if (photo.isEmpty()) {
            throw new InvalidRequestException("Photo file must not be empty");
        }

        String trustedCourierId = requireCourierId();
        DeliveryPhotoProofReceipt receipt = photoProofService.upload(
                id, assignmentId, trustedCourierId, photo.getContentType(), photo.getBytes());

        return ResponseEntity.ok(new DeliveryPhotoProofResponse(
                receipt.proofId(), receipt.format(), receipt.byteSize(), receipt.uploadedAt()));
    }

    @PostMapping("/{id}/complete")
    public ResponseEntity<DeliveryCompletionResponse> completeDelivery(
            @PathVariable String id,
            @Valid @RequestBody DeliveryCompletionRequest request) {

        String trustedCourierId = requireCourierId();
        DeliveryCompletionResult result = completionService.complete(
                id, request.challengeId(), request.photoProofId(), trustedCourierId);

        return ResponseEntity.ok(new DeliveryCompletionResponse(
                result.parcelId(), result.status(), result.completedAt()));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<ParcelStatusUpdateResponse> updateParcelStatus(
            @PathVariable String id,
            @Valid @RequestBody ParcelStatusUpdateRequest request) {

        String trustedCourierId = requireCourierId();
        try {
            ParcelStatusHistory history = statusService.updateStatus(
                    id, trustedCourierId, request.status(), request.failureReason());

            return ResponseEntity.ok(new ParcelStatusUpdateResponse(
                    history.getParcelId(), history.getPreviousStatus(), history.getNewStatus(), history.getChangedAt()));
        } catch (com.swiftdrop.parcel.exception.ParcelStatusConflictException ex) {
            if (parcelRepository != null && historyRepository != null) {
                var parcel = parcelRepository.findById(id);
                if (parcel.isPresent() && parcel.get().getStatus() != null && parcel.get().getStatus().equalsIgnoreCase(request.status())) {
                    var latest = historyRepository.findFirstByParcelIdOrderByChangedAtDesc(id);
                    if (latest.isPresent()) {
                        var h = latest.get();
                        return ResponseEntity.ok(new ParcelStatusUpdateResponse(
                                h.getParcelId(), h.getPreviousStatus(), h.getNewStatus(), h.getChangedAt()));
                    }
                }
            }
            throw ex;
        }
    }

    private String requireCourierId() {
        UserPrincipal principal = CurrentUser.require();
        if (!CurrentUser.isCourier(principal)) {
            throw new CourierAssignmentMismatchException("Courier role is required");
        }
        return principal.getId().toString();
    }
}
