package com.swiftdrop.pricing.controller;

import com.swiftdrop.pricing.client.ParcelServiceClient;
import com.swiftdrop.pricing.client.dto.ParcelResponseDto;
import com.swiftdrop.pricing.service.ReceiptPdfService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import com.swiftdrop.pricing.security.UserPrincipal;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

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
    public ResponseEntity<byte[]> downloadReceipt(@PathVariable String trackingNumber) {
        UserPrincipal principal = requirePrincipal();

        ParcelResponseDto parcel = parcelServiceClient.getParcelByTrackingNumber(trackingNumber);
        if (parcel == null) {
            throw new com.swiftdrop.pricing.exception.ResourceNotFoundException("Parcel not found: " + trackingNumber);
        }

        verifyReceiptAccess(parcel, principal);

        byte[] pdfContent = receiptPdfService.generateParcelReceipt(parcel);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"receipt-" + trackingNumber + ".pdf\"")
                .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate")
                .body(pdfContent);
    }

    private UserPrincipal requirePrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserPrincipal principal && principal.getId() != null) {
            return principal;
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
    }

    private void verifyReceiptAccess(ParcelResponseDto parcel, UserPrincipal principal) {
        String role = principal.getRole() == null ? "" : principal.getRole().toLowerCase(java.util.Locale.ROOT);
        if (role.equals("admin") || role.equals("dispatcher") || role.equals("staff")) {
            return;
        }
        String senderId = parcel.getSenderId();
        if (senderId != null && senderId.trim().equalsIgnoreCase(principal.getId().toString())) {
            return;
        }
        throw new com.swiftdrop.pricing.exception.AccessDeniedException(
                "Access denied: You can only download receipts for your own parcels");
    }
}
