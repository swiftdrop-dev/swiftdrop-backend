package com.swiftdrop.parcel.controller;

import com.swiftdrop.parcel.entity.Parcel;
import com.swiftdrop.parcel.exception.ParcelNotFoundException;
import com.swiftdrop.parcel.exception.ParcelRestExceptionHandler;
import com.swiftdrop.parcel.repository.ParcelRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CustomerParcelController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(ParcelRestExceptionHandler.class)
class CustomerParcelControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ParcelRepository parcelRepository;

    @MockBean
    private com.swiftdrop.parcel.service.ParcelBookingService parcelBookingService;

    @Test
    void booksParcelSuccessfullyAndMarksFeePending() throws Exception {
        when(parcelBookingService.bookParcel(any(), any())).thenAnswer(invocation -> {
            com.swiftdrop.parcel.dto.request.CreateParcelBookingRequest req = invocation.getArgument(0);
            String sender = invocation.getArgument(1);
            Parcel p = new Parcel("prc_001", null, "SWF-2026-A1B2C3D4", "CREATED", null,
                    req.recipientName(), req.recipientPhone(), req.recipientAddress());
            p.setSenderId(sender);
            p.setWeightKg(req.weightKg());
            p.setFragile(req.fragile());
            p.setTotalFee(req.totalFee());
            p.setPaymentStatus("UNPAID");
            p.setCreatedAt(Instant.parse("2026-11-22T10:00:00Z"));
            return p;
        });

        String payload = """
                {
                    "recipientName": "Kamal Perera",
                    "recipientPhone": "+94771234567",
                    "recipientAddress": "45 Galle Road, Colombo 03",
                    "pickupAddress": "12 Warehouse Road, Colombo 10",
                    "description": "Fragile glassware",
                    "weightKg": 2.50,
                    "parcelSize": "MEDIUM",
                    "fragile": true,
                    "paymentMethod": "COD"
                }
                """;

        mockMvc.perform(post("/api/v1/parcels")
                        .header("X-User-Id", "usr_customer_123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.trackingNumber").value(org.hamcrest.Matchers.matchesPattern("SWF-\\d{4}-[0-9A-F]{8}")))
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.senderId").value("usr_customer_123"))
                .andExpect(jsonPath("$.recipientName").value("Kamal Perera"))
                .andExpect(jsonPath("$.weightKg").value(2.50))
                .andExpect(jsonPath("$.fragile").value(true))
                .andExpect(jsonPath("$.totalFee").isEmpty())
                .andExpect(jsonPath("$.feeStatus").value("PENDING_PRICING_SERVICE"))
                .andExpect(jsonPath("$.paymentStatus").value("UNPAID"));
    }

    @Test
    void rejectsInvalidWeightOrMissingFields() throws Exception {
        String invalidPayload = """
                {
                    "recipientName": "",
                    "weightKg": -1.0
                }
                """;

        mockMvc.perform(post("/api/v1/parcels")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidPayload))
                .andExpect(status().isBadRequest());
    }

    @Test
    void retrievesCustomerParcelsWithPagination() throws Exception {
        Parcel p = new Parcel("prc_001", null, "SWF-2026-A1B2C3D4", "CREATED", null,
                "Kamal Perera", "+94771234567", "45 Galle Road");
        p.setSenderId("usr_customer_123");
        p.setCreatedAt(Instant.parse("2026-11-22T10:00:00Z"));

        when(parcelRepository.findBySenderIdOrderByCreatedAtDesc(eq("usr_customer_123"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(p)));

        mockMvc.perform(get("/api/v1/parcels/customer/me")
                        .header("X-User-Id", "usr_customer_123")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value("prc_001"))
                .andExpect(jsonPath("$.content[0].trackingNumber").value("SWF-2026-A1B2C3D4"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.hasNext").value(false));
    }

    @Test
    void returns404WhenParcelNotFound() throws Exception {
        when(parcelRepository.findById("missing_id")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/parcels/missing_id"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void retrievesParcelByTrackingNumber() throws Exception {
        Parcel p = new Parcel("prc_001", null, "SWF-2026-A1B2C3D4", "CREATED", null,
                "Kamal Perera", "+94771234567", "45 Galle Road");
        p.setCreatedAt(Instant.parse("2026-11-22T10:00:00Z"));

        when(parcelRepository.findByTrackingNumber("SWF-2026-A1B2C3D4")).thenReturn(Optional.of(p));

        mockMvc.perform(get("/api/v1/parcels/track/SWF-2026-A1B2C3D4"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("prc_001"))
                .andExpect(jsonPath("$.trackingNumber").value("SWF-2026-A1B2C3D4"))
                .andExpect(jsonPath("$.status").value("CREATED"));
    }

    @Test
    void booksParcelWithCalculatedFeeSuccessfully() throws Exception {
        when(parcelBookingService.bookParcel(any(), any())).thenAnswer(invocation -> {
            com.swiftdrop.parcel.dto.request.CreateParcelBookingRequest req = invocation.getArgument(0);
            String sender = invocation.getArgument(1);
            Parcel p = new Parcel("prc_002", null, "SWF-2026-A1B2C3D4", "CREATED", null,
                    req.recipientName(), req.recipientPhone(), req.recipientAddress());
            p.setSenderId(sender);
            p.setWeightKg(req.weightKg());
            p.setFragile(req.fragile());
            p.setTotalFee(req.totalFee());
            p.setPaymentStatus("UNPAID");
            p.setCreatedAt(Instant.parse("2026-11-22T10:00:00Z"));
            return p;
        });

        String payload = """
                {
                    "recipientName": "Kamal Perera",
                    "recipientPhone": "+94771234567",
                    "recipientAddress": "45 Galle Road, Colombo 03",
                    "pickupAddress": "12 Warehouse Road, Colombo 10",
                    "description": "Electronics",
                    "weightKg": 3.0,
                    "parcelSize": "STANDARD",
                    "fragile": false,
                    "paymentMethod": "COD",
                    "totalFee": 660.00
                }
                """;

        mockMvc.perform(post("/api/v1/parcels")
                        .header("X-User-Id", "usr_customer_123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.totalFee").value(660.00))
                .andExpect(jsonPath("$.feeStatus").value("CALCULATED"));
    }

    @Test
    void retrievesParcelByIdOrTrackingNumberFallback() throws Exception {
        Parcel p = new Parcel("prc_001", null, "SWF-2026-A1B2C3D4", "CREATED", null,
                "Kamal Perera", "+94771234567", "45 Galle Road");
        p.setCreatedAt(Instant.parse("2026-11-22T10:00:00Z"));

        when(parcelRepository.findById("SWF-2026-A1B2C3D4")).thenReturn(Optional.empty());
        when(parcelRepository.findByTrackingNumber("SWF-2026-A1B2C3D4")).thenReturn(Optional.of(p));

        mockMvc.perform(get("/api/v1/parcels/SWF-2026-A1B2C3D4"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("prc_001"))
                .andExpect(jsonPath("$.trackingNumber").value("SWF-2026-A1B2C3D4"));
    }
}
