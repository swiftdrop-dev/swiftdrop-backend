package com.swiftdrop.tracking.controller;

import com.swiftdrop.tracking.dto.response.CustomerTrackingResponse;
import com.swiftdrop.tracking.dto.response.PublicTrackingResponse;
import com.swiftdrop.tracking.dto.response.TrackingTimelineItemDto;
import com.swiftdrop.tracking.exception.AccessDeniedException;
import com.swiftdrop.tracking.exception.UnauthorizedException;
import com.swiftdrop.tracking.service.TrackingQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TrackingController.class)
@AutoConfigureMockMvc(addFilters = false)
class TrackingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TrackingQueryService trackingQueryService;

    @Test
    void getPublicTracking_Returns200AndPrivacySafePayload() throws Exception {
        String trackingNumber = "SWF-12345";
        PublicTrackingResponse response = new PublicTrackingResponse(
                trackingNumber,
                "in_transit",
                Instant.parse("2026-11-22T14:00:00Z"),
                List.of(
                        new TrackingTimelineItemDto("pending", "Booking Confirmed", "Registered", Instant.parse("2026-11-22T10:00:00Z"), "Colombo"),
                        new TrackingTimelineItemDto("in_transit", "In Transit", "Moving", Instant.parse("2026-11-22T14:00:00Z"), "Colombo Hub")
                )
        );

        when(trackingQueryService.getPublicTracking(trackingNumber)).thenReturn(response);

        mockMvc.perform(get("/api/v1/tracking/public/" + trackingNumber))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trackingNumber").value(trackingNumber))
                .andExpect(jsonPath("$.currentStatus").value("in_transit"))
                .andExpect(jsonPath("$.timeline[0].status").value("pending"))
                .andExpect(jsonPath("$.timeline[0].title").value("Booking Confirmed"))
                // Ensure privacy: no sender/recipient info in response
                .andExpect(jsonPath("$.senderPhone").doesNotExist())
                .andExpect(jsonPath("$.recipientPhone").doesNotExist())
                .andExpect(jsonPath("$.courierId").doesNotExist());
    }

    @Test
    void getCustomerTracking_Returns200WhenOwnerAccesses() throws Exception {
        String trackingNumber = "SWF-12345";
        CustomerTrackingResponse response = new CustomerTrackingResponse(
                trackingNumber,
                "prc_999",
                "in_transit",
                Instant.parse("2026-11-22T14:00:00Z"),
                List.of(
                        new TrackingTimelineItemDto("pending", "Booking Confirmed", "Registered", Instant.parse("2026-11-22T10:00:00Z"), "Colombo")
                )
        );

        when(trackingQueryService.getCustomerTracking(trackingNumber, "user-123", "CUSTOMER")).thenReturn(response);

        mockMvc.perform(get("/api/v1/tracking/" + trackingNumber)
                        .header("X-User-Id", "user-123")
                        .header("X-User-Roles", "CUSTOMER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trackingNumber").value(trackingNumber))
                .andExpect(jsonPath("$.parcelId").value("prc_999"))
                .andExpect(jsonPath("$.currentStatus").value("in_transit"));
    }

    @Test
    void getCustomerTracking_Returns401WhenIdentityMissing() throws Exception {
        String trackingNumber = "SWF-12345";
        when(trackingQueryService.getCustomerTracking(trackingNumber, null, null))
                .thenThrow(new UnauthorizedException("Authentication required"));

        mockMvc.perform(get("/api/v1/tracking/" + trackingNumber))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getCustomerTracking_Returns403WhenNonOwnerAttemptsAccess() throws Exception {
        String trackingNumber = "SWF-12345";
        when(trackingQueryService.getCustomerTracking(trackingNumber, "attacker", "CUSTOMER"))
                .thenThrow(new AccessDeniedException("Access denied"));

        mockMvc.perform(get("/api/v1/tracking/" + trackingNumber)
                        .header("X-User-Id", "attacker")
                        .header("X-User-Roles", "CUSTOMER"))
                .andExpect(status().isForbidden());
    }
}
