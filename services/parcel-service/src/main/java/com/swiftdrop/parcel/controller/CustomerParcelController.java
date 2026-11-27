package com.swiftdrop.parcel.controller;

import com.swiftdrop.parcel.dto.request.CreateParcelBookingRequest;
import com.swiftdrop.parcel.dto.response.ParcelBookingResponse;
import com.swiftdrop.parcel.dto.response.ParcelPageResponse;
import com.swiftdrop.parcel.entity.Parcel;
import com.swiftdrop.parcel.exception.InvalidRequestException;
import com.swiftdrop.parcel.exception.ParcelNotFoundException;
import com.swiftdrop.parcel.repository.ParcelRepository;
import com.swiftdrop.parcel.security.CurrentUser;
import com.swiftdrop.parcel.security.UserPrincipal;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/parcels")
public class CustomerParcelController {

    private final ParcelRepository parcelRepository;
    private final com.swiftdrop.parcel.service.ParcelBookingService parcelBookingService;

    public CustomerParcelController(ParcelRepository parcelRepository,
                                  com.swiftdrop.parcel.service.ParcelBookingService parcelBookingService) {
        this.parcelRepository = parcelRepository;
        this.parcelBookingService = parcelBookingService;
    }

    @PostMapping
    public ResponseEntity<ParcelBookingResponse> bookParcel(
            @Valid @RequestBody CreateParcelBookingRequest request) {

        String senderId = resolveUserId();
        Parcel saved = parcelBookingService.bookParcel(request, senderId);
        ParcelBookingResponse response = toBookingResponse(saved);
        return ResponseEntity.created(URI.create("/api/v1/parcels/" + saved.getId())).body(response);
    }

    @GetMapping("/customer/me")
    public ResponseEntity<ParcelPageResponse<ParcelBookingResponse>> getMyParcels(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        if (page < 0) {
            throw new InvalidRequestException("Page index must not be negative");
        }
        if (size < 1 || size > 100) {
            throw new InvalidRequestException("Page size must be between 1 and 100");
        }

        String senderId = resolveUserId();
        var pageable = PageRequest.of(page, size);
        Page<Parcel> resultPage;

        if (status != null && !status.isBlank()) {
            resultPage = parcelRepository.findBySenderIdAndStatusOrderByCreatedAtDesc(senderId, status, pageable);
        } else {
            resultPage = parcelRepository.findBySenderIdOrderByCreatedAtDesc(senderId, pageable);
        }

        var content = resultPage.getContent().stream().map(this::toBookingResponse).toList();
        return ResponseEntity.ok(new ParcelPageResponse<>(content, page, size, resultPage.hasNext()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ParcelBookingResponse> getParcelById(@PathVariable String id) {
        Parcel parcel = parcelRepository.findById(id)
                .or(() -> parcelRepository.findByTrackingNumber(id))
                .orElseThrow(() -> new ParcelNotFoundException(id));
        assertCanRead(parcel, id);
        return ResponseEntity.ok(toBookingResponse(parcel));
    }

    @GetMapping("/track/{trackingNumber}")
    public ResponseEntity<ParcelBookingResponse> getParcelByTrackingNumber(
            @PathVariable String trackingNumber) {
        Parcel parcel = parcelRepository.findByTrackingNumber(trackingNumber)
                .orElseThrow(() -> new ParcelNotFoundException(trackingNumber));
        assertCanRead(parcel, trackingNumber);
        return ResponseEntity.ok(toBookingResponse(parcel));
    }

    private String resolveUserId() {
        return CurrentUser.require().getId().toString();
    }

    private void assertCanRead(Parcel parcel, String requestedId) {
        UserPrincipal principal = CurrentUser.require();
        if (CurrentUser.canReadAnyParcel(principal)) {
            return;
        }
        String senderId = parcel.getSenderId();
        if (senderId != null && senderId.equalsIgnoreCase(principal.getId().toString())) {
            return;
        }
        throw new ParcelNotFoundException(requestedId);
    }

    private ParcelBookingResponse toBookingResponse(Parcel p) {
        return new ParcelBookingResponse(
                p.getId(),
                p.getTrackingNumber(),
                p.getStatus(),
                p.getSenderId(),
                p.getSenderName(),
                p.getSenderPhone(),
                p.getRecipientName(),
                p.getRecipientPhone(),
                p.getRecipientAddress(),
                p.getPickupAddress(),
                p.getWeightKg(),
                p.getParcelSize(),
                p.getDescription(),
                p.getFragile(),
                p.getTotalFee(),
                p.getTotalFee() == null ? "PENDING_PRICING_SERVICE" : "CALCULATED",
                p.getPaymentMethod(),
                p.getPaymentStatus(),
                p.getCourierId(),
                p.getEstimatedDeliveryDate(),
                p.getCreatedAt(),
                p.getUpdatedAt()
        );
    }
}
