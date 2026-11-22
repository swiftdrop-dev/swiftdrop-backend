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
    public ResponseEntity<byte[]> downloadReceipt(@PathVariable String trackingNumber) {
        ParcelResponseDto parcel = parcelServiceClient.getParcelByTrackingNumber(trackingNumber);
        byte[] pdfContent = receiptPdfService.generateParcelReceipt(parcel);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"receipt-" + trackingNumber + ".pdf\"")
                .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate")
                .body(pdfContent);
    }
}
