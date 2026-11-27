package com.swiftdrop.pricing.controller;

import com.swiftdrop.pricing.client.ParcelServiceClient;
import com.swiftdrop.pricing.client.dto.ParcelResponseDto;
import com.swiftdrop.pricing.exception.GlobalExceptionHandler;
import com.swiftdrop.pricing.security.UserPrincipal;
import com.swiftdrop.pricing.service.ReceiptPdfService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ReceiptController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
@DisplayName("ReceiptController Authorization and Download Tests")
class ReceiptControllerTest {

    private static final UUID SENDER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OTHER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID ADMIN_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @Autowired
    private MockMvc mockMvc;

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    private void authenticate(UUID userId, String role) {
        UserPrincipal principal = new UserPrincipal(userId, role + "@swiftdrop.test", role);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    @MockBean
    private ReceiptPdfService receiptPdfService;

    @MockBean
    private ParcelServiceClient parcelServiceClient;

    @Test
    @DisplayName("Authorized sender downloads PDF receipt successfully")
    void authorizedSenderDownloadsReceipt() throws Exception {
        ParcelResponseDto parcel = ParcelResponseDto.builder()
                .id("prc_123")
                .trackingNumber("SWF-2026-ABCD1234")
                .senderId(SENDER_ID.toString())
                .senderName("Kamal Silva")
                .finalFee(new BigDecimal("750.00"))
                .status("DELIVERED")
                .createdAt(Instant.now())
                .build();

        byte[] samplePdf = "%PDF-1.4 test binary data".getBytes();

        when(parcelServiceClient.getParcelByTrackingNumber("SWF-2026-ABCD1234")).thenReturn(parcel);
        when(receiptPdfService.generateParcelReceipt(any(ParcelResponseDto.class))).thenReturn(samplePdf);

        authenticate(SENDER_ID, "customer");
        mockMvc.perform(get("/api/v1/receipts/SWF-2026-ABCD1234")
                        .header("X-User-Id", OTHER_ID.toString())
                        .header("X-User-Roles", "admin"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PDF_VALUE))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"receipt-SWF-2026-ABCD1234.pdf\""))
                .andExpect(content().bytes(samplePdf));
    }

    @Test
    @DisplayName("Admin or Dispatcher can download receipt for any parcel")
    void privilegedStaffDownloadsReceipt() throws Exception {
        ParcelResponseDto parcel = ParcelResponseDto.builder()
                .id("prc_123")
                .trackingNumber("SWF-2026-ABCD1234")
                .senderId(SENDER_ID.toString())
                .finalFee(new BigDecimal("750.00"))
                .build();

        byte[] samplePdf = "%PDF-1.4 admin override".getBytes();

        when(parcelServiceClient.getParcelByTrackingNumber("SWF-2026-ABCD1234")).thenReturn(parcel);
        when(receiptPdfService.generateParcelReceipt(any(ParcelResponseDto.class))).thenReturn(samplePdf);

        authenticate(ADMIN_ID, "admin");
        mockMvc.perform(get("/api/v1/receipts/SWF-2026-ABCD1234")
                        .header("X-User-Id", OTHER_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(content().bytes(samplePdf));
    }

    @Test
    @DisplayName("Unauthorized user is rejected with 403 Forbidden")
    void unauthorizedUserRejectedWith403() throws Exception {
        ParcelResponseDto parcel = ParcelResponseDto.builder()
                .id("prc_123")
                .trackingNumber("SWF-2026-ABCD1234")
                .senderId(SENDER_ID.toString())
                .finalFee(new BigDecimal("750.00"))
                .build();

        when(parcelServiceClient.getParcelByTrackingNumber("SWF-2026-ABCD1234")).thenReturn(parcel);

        authenticate(OTHER_ID, "customer");
        mockMvc.perform(get("/api/v1/receipts/SWF-2026-ABCD1234")
                        .header("X-User-Id", SENDER_ID.toString()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"));
    }

    @Test
    @DisplayName("Unauthenticated receipt download is rejected")
    void unauthenticatedReceiptIsRejected() throws Exception {
        when(parcelServiceClient.getParcelByTrackingNumber("SWF-2026-ABCD1234")).thenReturn(
                ParcelResponseDto.builder().trackingNumber("SWF-2026-ABCD1234").senderId(SENDER_ID.toString()).build());

        mockMvc.perform(get("/api/v1/receipts/SWF-2026-ABCD1234"))
                .andExpect(status().isUnauthorized());
    }
}
