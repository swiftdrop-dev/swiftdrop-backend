package com.swiftdrop.pricing.controller;

import com.swiftdrop.pricing.client.ParcelServiceClient;
import com.swiftdrop.pricing.client.dto.ParcelResponseDto;
import com.swiftdrop.pricing.service.ReceiptPdfService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/receipts")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@Tag(name = "Receipts", description = "Dynamic PDF generation for parcel receipts and invoices")
public class ReceiptController {

    private final ReceiptPdfService receiptPdfService;
    private final ParcelServiceClient parcelServiceClient;

    @GetMapping("/{trackingNumber}")
    @Operation(summary = "Download official parcel receipt as PDF", description = "Streams a dynamically generated PDF document")
    public ResponseEntity<byte[]> downloadReceipt(
            @PathVariable String trackingNumber,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles) {

        ParcelResponseDto parcel = parcelServiceClient.getParcelByTrackingNumber(trackingNumber);
        if (parcel == null) {
            throw new com.swiftdrop.pricing.exception.ResourceNotFoundException("Parcel not found: " + trackingNumber);
        }

        verifyReceiptAccess(parcel, userId, userRoles);

        byte[] pdfContent = receiptPdfService.generateParcelReceipt(parcel);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"receipt-" + trackingNumber + ".pdf\"")
                .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate")
                .body(pdfContent);
    }

    private void verifyReceiptAccess(ParcelResponseDto parcel, String userId, String userRoles) {
        org.springframework.security.core.Authentication auth =
                org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        String effectiveUserId = userId;
        String effectiveRoles = userRoles;
        if (auth != null && auth.getPrincipal() instanceof com.swiftdrop.pricing.security.UserPrincipal principal) {
            effectiveUserId = principal.getId() != null ? principal.getId().toString() : principal.getEmail();
            effectiveRoles = "ROLE_" + principal.getRole().toUpperCase(java.util.Locale.ROOT);
        }

        if (effectiveUserId == null || effectiveUserId.isBlank()) {
            return;
        }

        boolean isPrivileged = effectiveRoles != null && (
                effectiveRoles.toLowerCase().contains("admin") ||
                effectiveRoles.toLowerCase().contains("dispatcher") ||
                effectiveRoles.toLowerCase().contains("courier") ||
                effectiveRoles.toLowerCase().contains("staff")
        );

        if (isPrivileged) {
            return;
        }

        if (parcel.getSenderId() != null && !parcel.getSenderId().isBlank()) {
            if (!parcel.getSenderId().trim().equalsIgnoreCase(effectiveUserId.trim())) {
                throw new com.swiftdrop.pricing.exception.AccessDeniedException(
                        "Access denied: You can only download receipts for your own parcels");
            }
        }
    }
}
