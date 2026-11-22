package com.swiftdrop.parcel.controller;

import com.swiftdrop.parcel.entity.Parcel;
import com.swiftdrop.parcel.entity.ParcelAssignment;
import com.swiftdrop.parcel.entity.ParcelStatusHistory;
import com.swiftdrop.parcel.exception.ParcelRestExceptionHandler;
import com.swiftdrop.parcel.otp.OtpChallengeReceipt;
import com.swiftdrop.parcel.otp.OtpChallengeService;
import com.swiftdrop.parcel.otp.OtpVerificationOutcome;
import com.swiftdrop.parcel.otp.OtpVerificationResult;
import com.swiftdrop.parcel.photo.DeliveryPhotoProofReceipt;
import com.swiftdrop.parcel.photo.DeliveryPhotoProofService;
import com.swiftdrop.parcel.repository.ParcelAssignmentRepository;
import com.swiftdrop.parcel.repository.ParcelRepository;
import com.swiftdrop.parcel.service.DeliveryCompletionResult;
import com.swiftdrop.parcel.service.DeliveryCompletionService;
import com.swiftdrop.parcel.service.ParcelStatusService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ParcelDeliveryController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(ParcelRestExceptionHandler.class)
class ParcelDeliveryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ParcelRepository parcelRepository;

    @MockBean
    private ParcelAssignmentRepository assignmentRepository;

    @MockBean
    private OtpChallengeService otpChallengeService;

    @MockBean
    private DeliveryPhotoProofService photoProofService;

    @MockBean
    private DeliveryCompletionService completionService;

    @MockBean
    private ParcelStatusService statusService;

    @Test
    void getsAssignedDeliveriesForCourier() throws Exception {
        ParcelAssignment assignment = new ParcelAssignment("prc_001", "cou_001",
                Instant.parse("2026-11-22T08:00:00Z"), "dispatcher", "Auto");
        Parcel parcel = new Parcel("prc_001", "cou_001", "SWF-2026-00112233", "in_transit",
                "2026-11-22", "Nimal", "+94771112233", "123 Main St");

        when(assignmentRepository.findByCourierIdAndUnassignedAtIsNullOrderByAssignedAtDesc(eq("cou_001"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(assignment)));
        when(parcelRepository.findByIdInAndCourierId(List.of("prc_001"), "cou_001"))
                .thenReturn(List.of(parcel));

        mockMvc.perform(get("/api/v1/parcels/me/assigned-deliveries")
                        .header("X-User-Id", "cou_001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].parcelId").value("prc_001"))
                .andExpect(jsonPath("$.content[0].trackingNumber").value("SWF-2026-00112233"))
                .andExpect(jsonPath("$.content[0].status").value("in_transit"))
                .andExpect(jsonPath("$.content[0].recipientName").value("Nimal"))
                .andExpect(jsonPath("$.content[0].deliveryAddress").value("123 Main St"));
    }

    @Test
    void requestsDeliveryOtpSuccessfully() throws Exception {
        UUID challengeId = UUID.randomUUID();
        when(otpChallengeService.issue("prc_001", "cou_001"))
                .thenReturn(new OtpChallengeReceipt(challengeId, Instant.parse("2026-11-22T10:05:00Z"),
                        Instant.parse("2026-11-22T10:01:00Z"), "+94******567"));

        mockMvc.perform(post("/api/v1/parcels/prc_001/delivery-otp")
                        .header("X-User-Id", "cou_001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.challengeId").value(challengeId.toString()))
                .andExpect(jsonPath("$.destinationMasked").value("+94******567"));
    }

    @Test
    void verifiesDeliveryOtp() throws Exception {
        UUID challengeId = UUID.randomUUID();
        when(otpChallengeService.verify(eq("prc_001"), eq(challengeId), eq("cou_001"), any(char[].class)))
                .thenReturn(new OtpVerificationResult(challengeId, OtpVerificationOutcome.VERIFIED,
                        Instant.parse("2026-11-22T10:02:00Z")));

        mockMvc.perform(post("/api/v1/parcels/prc_001/delivery-otp/verify")
                        .header("X-User-Id", "cou_001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"challengeId\":\"" + challengeId + "\",\"code\":\"123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.challengeId").value(challengeId.toString()))
                .andExpect(jsonPath("$.outcome").value("VERIFIED"))
                .andExpect(jsonPath("$.verifiedAt").exists());
    }

    @Test
    void uploadsDeliveryPhotoProof() throws Exception {
        UUID proofId = UUID.randomUUID();
        MockMultipartFile file = new MockMultipartFile(
                "photo", "photo.jpg", MediaType.IMAGE_JPEG_VALUE, "dummy-image-bytes".getBytes());

        when(photoProofService.upload(eq("prc_001"), eq(10L), eq("cou_001"), eq("image/jpeg"), any(byte[].class)))
                .thenReturn(new DeliveryPhotoProofReceipt(proofId, "jpeg", 17L, Instant.parse("2026-11-22T10:03:00Z")));

        mockMvc.perform(multipart("/api/v1/parcels/prc_001/delivery-photo")
                        .file(file)
                        .param("assignmentId", "10")
                        .header("X-User-Id", "cou_001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.proofId").value(proofId.toString()))
                .andExpect(jsonPath("$.format").value("jpeg"))
                .andExpect(jsonPath("$.byteSize").value(17));
    }

    @Test
    void completesDelivery() throws Exception {
        UUID challengeId = UUID.randomUUID();
        UUID photoProofId = UUID.randomUUID();

        when(completionService.complete("prc_001", challengeId, photoProofId, "cou_001"))
                .thenReturn(new DeliveryCompletionResult("prc_001", "delivered",
                        Instant.parse("2026-11-22T10:04:00Z")));

        mockMvc.perform(post("/api/v1/parcels/prc_001/complete")
                        .header("X-User-Id", "cou_001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"challengeId\":\"" + challengeId + "\",\"photoProofId\":\"" + photoProofId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parcelId").value("prc_001"))
                .andExpect(jsonPath("$.status").value("delivered"))
                .andExpect(jsonPath("$.completedAt").exists());
    }

    @Test
    void updatesParcelStatus() throws Exception {
        when(statusService.updateStatus("prc_001", "cou_001", "in_transit", null))
                .thenReturn(new ParcelStatusHistory("prc_001", "picked_up", "in_transit",
                        Instant.parse("2026-11-22T09:00:00Z"), "cou_001", null));

        mockMvc.perform(patch("/api/v1/parcels/prc_001/status")
                        .header("X-User-Id", "cou_001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"in_transit\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parcelId").value("prc_001"))
                .andExpect(jsonPath("$.previousStatus").value("picked_up"))
                .andExpect(jsonPath("$.status").value("in_transit"));
    }
}
