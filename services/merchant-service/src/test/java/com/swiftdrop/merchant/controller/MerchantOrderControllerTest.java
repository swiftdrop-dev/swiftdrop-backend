package com.swiftdrop.merchant.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.merchant.dto.request.BulkMerchantOrderRequest;
import com.swiftdrop.merchant.dto.request.MerchantOrderAddressRequest;
import com.swiftdrop.merchant.dto.request.MerchantOrderCreateRequest;
import com.swiftdrop.merchant.dto.request.MerchantRecipientRequest;
import com.swiftdrop.merchant.dto.response.BulkMerchantOrderResponse;
import com.swiftdrop.merchant.dto.response.MerchantOrderDetailsDto;
import com.swiftdrop.merchant.dto.response.MerchantOrderPageResponse;
import com.swiftdrop.merchant.dto.response.MerchantOrderResponse;
import com.swiftdrop.merchant.entity.Merchant;
import com.swiftdrop.merchant.entity.enums.MerchantStatus;
import com.swiftdrop.merchant.exception.GlobalExceptionHandler;
import com.swiftdrop.merchant.exception.ResourceNotFoundException;
import com.swiftdrop.merchant.exception.UnauthenticatedException;
import com.swiftdrop.merchant.exception.UnauthorizedException;
import com.swiftdrop.merchant.service.MerchantOrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class MerchantOrderControllerTest {

    @Mock
    private MerchantOrderService merchantOrderService;

    @InjectMocks
    private MerchantOrderController controller;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private Merchant approvedMerchant;
    private MerchantOrderResponse sampleOrderResponse;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        approvedMerchant = new Merchant();
        approvedMerchant.setId(UUID.randomUUID());
        approvedMerchant.setUserId(UUID.randomUUID());
        approvedMerchant.setBusinessName("Test Store Ltd");
        approvedMerchant.setStatus(MerchantStatus.APPROVED);

        MerchantOrderDetailsDto details = new MerchantOrderDetailsDto(
                "REF-001",
                new MerchantRecipientRequest("John Doe", "+94771234567"),
                new MerchantOrderAddressRequest("123 Warehouse Rd", "Colombo", "Colombo", "00100"),
                new MerchantOrderAddressRequest("456 Customer St", "Kandy", "Kandy", "20000"),
                "Electronics package",
                new BigDecimal("2.500"),
                "MD",
                new BigDecimal("5000.00"),
                false,
                "Fragile handle with care",
                "COD"
        );

        sampleOrderResponse = new MerchantOrderResponse(
                UUID.randomUUID(),
                "REF-001",
                "SWF-2026-TEST001",
                "prc_test001",
                "CREATED",
                details,
                new BigDecimal("425.00"),
                new BigDecimal("425.00"),
                "LKR",
                Instant.now(),
                Instant.now()
        );
    }

    @Test
    void shouldCreateOrderSuccessfullyWithApiKey() throws Exception {
        MerchantOrderCreateRequest request = new MerchantOrderCreateRequest();
        request.setMerchantOrderRef("REF-001");
        request.setRecipient(new MerchantRecipientRequest("John Doe", "+94771234567"));
        request.setPickupAddress(new MerchantOrderAddressRequest("123 Warehouse Rd", "Colombo", "Colombo", "00100"));
        request.setDeliveryAddress(new MerchantOrderAddressRequest("456 Customer St", "Kandy", "Kandy", "20000"));
        request.setWeightKg(new BigDecimal("2.500"));
        request.setSize("MD");
        request.setPaymentMethod("COD");

        when(merchantOrderService.resolveMerchant(eq("sk_live_valid123"), any(), any()))
                .thenReturn(approvedMerchant);
        when(merchantOrderService.createOrder(eq(approvedMerchant), any(MerchantOrderCreateRequest.class), any()))
                .thenReturn(sampleOrderResponse);

        mockMvc.perform(post("/api/v1/merchants/orders")
                        .header("X-API-Key", "sk_live_valid123")
                        .header("Idempotency-Key", "idemp-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.merchantOrderRef").value("REF-001"))
                .andExpect(jsonPath("$.trackingNumber").value("SWF-2026-TEST001"))
                .andExpect(jsonPath("$.currency").value("LKR"))
                .andExpect(jsonPath("$.totalFee").value(425.00));
    }

    @Test
    void shouldRejectOrderCreationWhenApiKeyIsInvalid() throws Exception {
        MerchantOrderCreateRequest request = new MerchantOrderCreateRequest();
        request.setMerchantOrderRef("REF-001");
        request.setWeightKg(new BigDecimal("1.000"));

        when(merchantOrderService.resolveMerchant(eq("sk_live_bad"), any(), any()))
                .thenThrow(new UnauthenticatedException("Invalid, expired, or revoked API key"));

        mockMvc.perform(post("/api/v1/merchants/orders")
                        .header("X-API-Key", "sk_live_bad")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Invalid, expired, or revoked API key"));
    }

    @Test
    void shouldRejectOrderCreationWhenMerchantIsNotApproved() throws Exception {
        MerchantOrderCreateRequest request = new MerchantOrderCreateRequest();
        request.setMerchantOrderRef("REF-001");
        request.setWeightKg(new BigDecimal("1.000"));

        when(merchantOrderService.resolveMerchant(eq("sk_live_pending"), any(), any()))
                .thenThrow(new UnauthorizedException("Merchant account is not active or approved (current status: PENDING)"));

        mockMvc.perform(post("/api/v1/merchants/orders")
                        .header("X-API-Key", "sk_live_pending")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Merchant account is not active or approved (current status: PENDING)"));
    }

    @Test
    void shouldCreateBulkOrdersSuccessfully() throws Exception {
        MerchantOrderCreateRequest req1 = new MerchantOrderCreateRequest();
        req1.setMerchantOrderRef("REF-001");
        req1.setWeightKg(new BigDecimal("1.000"));

        BulkMerchantOrderRequest bulkRequest = new BulkMerchantOrderRequest(List.of(req1));
        BulkMerchantOrderResponse bulkResponse = new BulkMerchantOrderResponse(
                1, 1, 0,
                List.of(sampleOrderResponse),
                Collections.emptyList()
        );

        when(merchantOrderService.resolveMerchant(eq("sk_live_valid123"), any(), any()))
                .thenReturn(approvedMerchant);
        when(merchantOrderService.createBulkOrders(eq(approvedMerchant), any(BulkMerchantOrderRequest.class)))
                .thenReturn(bulkResponse);

        mockMvc.perform(post("/api/v1/merchants/orders/bulk")
                        .header("X-API-Key", "sk_live_valid123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bulkRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.totalCount").value(1))
                .andExpect(jsonPath("$.successCount").value(1))
                .andExpect(jsonPath("$.failureCount").value(0))
                .andExpect(jsonPath("$.successfulOrders[0].trackingNumber").value("SWF-2026-TEST001"));
    }

    @Test
    void shouldListOrdersSuccessfully() throws Exception {
        MerchantOrderPageResponse pageResponse = new MerchantOrderPageResponse(
                List.of(sampleOrderResponse),
                1, 0, 20, 1
        );

        when(merchantOrderService.resolveMerchant(eq("sk_live_valid123"), any(), any()))
                .thenReturn(approvedMerchant);
        when(merchantOrderService.listOrders(eq(approvedMerchant), any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(pageResponse);

        mockMvc.perform(get("/api/v1/merchants/orders")
                        .header("X-API-Key", "sk_live_valid123")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.data[0].merchantOrderRef").value("REF-001"));
    }

    @Test
    void shouldGetOrderByIdSuccessfully() throws Exception {
        when(merchantOrderService.resolveMerchant(eq("sk_live_valid123"), any(), any()))
                .thenReturn(approvedMerchant);
        when(merchantOrderService.getOrder(eq(approvedMerchant), eq("REF-001")))
                .thenReturn(sampleOrderResponse);

        mockMvc.perform(get("/api/v1/merchants/orders/{id}", "REF-001")
                        .header("X-API-Key", "sk_live_valid123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.merchantOrderRef").value("REF-001"))
                .andExpect(jsonPath("$.trackingNumber").value("SWF-2026-TEST001"));
    }

    @Test
    void shouldReturn404WhenOrderNotFound() throws Exception {
        when(merchantOrderService.resolveMerchant(eq("sk_live_valid123"), any(), any()))
                .thenReturn(approvedMerchant);
        when(merchantOrderService.getOrder(eq(approvedMerchant), eq("NON_EXISTENT")))
                .thenThrow(new ResourceNotFoundException("Order not found with identifier: NON_EXISTENT"));

        mockMvc.perform(get("/api/v1/merchants/orders/{id}", "NON_EXISTENT")
                        .header("X-API-Key", "sk_live_valid123"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Order not found with identifier: NON_EXISTENT"));
    }
}
