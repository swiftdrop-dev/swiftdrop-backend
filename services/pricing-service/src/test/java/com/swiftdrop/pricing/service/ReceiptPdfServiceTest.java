package com.swiftdrop.pricing.service;

import com.swiftdrop.pricing.client.dto.ParcelResponseDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ReceiptPdfService Generation Unit Tests")
class ReceiptPdfServiceTest {

    private final ReceiptPdfService pdfService = new ReceiptPdfService();

    @Test
    @DisplayName("Generates valid non-empty PDF byte array with PDF header magic bytes")
    void testGenerateParcelReceipt() {
        ParcelResponseDto parcel = ParcelResponseDto.builder()
                .id(1L)
                .trackingNumber("SWD-77123-LK")
                .senderName("Sunil Perera")
                .senderPhone("+94 77 111 2233")
                .senderAddress("10 Galle Road, Colombo")
                .recipientName("Nimal Jayasinghe")
                .recipientPhone("+94 71 444 5566")
                .recipientAddress("25 Temple Street, Kandy")
                .originZone("Western Province (Colombo)")
                .destinationZone("Central Province (Kandy)")
                .weightKg(new BigDecimal("2.50"))
                .deliveryType("EXPRESS")
                .finalFee(new BigDecimal("1050.00"))
                .status("DELIVERED")
                .paymentStatus("PAID")
                .createdAt(Instant.now().minusSeconds(86400))
                .deliveredAt(Instant.now())
                .build();

        byte[] pdfBytes = pdfService.generateParcelReceipt(parcel);

        assertNotNull(pdfBytes);
        assertTrue(pdfBytes.length > 500, "PDF should contain valid rendered binary data");

        assertEquals('%', (char) pdfBytes[0]);
        assertEquals('P', (char) pdfBytes[1]);
        assertEquals('D', (char) pdfBytes[2]);
        assertEquals('F', (char) pdfBytes[3]);
    }
}
