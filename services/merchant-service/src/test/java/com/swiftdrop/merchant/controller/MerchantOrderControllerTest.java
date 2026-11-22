package com.swiftdrop.merchant.controller;
import com.swiftdrop.merchant.OrderTestData;
import com.swiftdrop.merchant.dto.response.*;
import com.swiftdrop.merchant.exception.*;
import com.swiftdrop.merchant.security.*;
import com.swiftdrop.merchant.service.MerchantOrderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
class MerchantOrderControllerTest {
    MerchantOrderService service; MerchantApiKeyVerifier verifier; MockMvc mvc; UUID merchant;
    final String parcel=OrderTestData.PARCEL_ID;
    @BeforeEach void setup() {
        service=mock(MerchantOrderService.class); verifier=mock(MerchantApiKeyVerifier.class); merchant=UUID.randomUUID();
        when(verifier.verify(eq("test-only"),anyString())).thenAnswer(i->new VerifiedMerchantApiCredential(merchant,UUID.randomUUID(),Set.of(i.<String>getArgument(1))));
        mvc=MockMvcBuilders.standaloneSetup(new MerchantOrderController(service))
            .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
            .addFilters(new MerchantOrderApiKeyFilter(new MerchantOrderAuthenticator(verifier),
                request -> "POST".equals(request.getMethod()), new ObjectMapper()))
            .setControllerAdvice(new MerchantOrderExceptionHandler(),new GlobalExceptionHandler()).build();
    }
    @Test void createHas201LocationNoStoreAndWriteScope() throws Exception {
        when(service.create(any(),eq("request-1"),any())).thenReturn(OrderTestData.response(merchant,parcel).order());
        mvc.perform(post("/api/v1/merchant/orders").header("X-API-Key","test-only").header("Idempotency-Key","request-1")
            .contentType("application/json").content(new ObjectMapper().writeValueAsString(OrderTestData.request())))
            .andExpect(status().isCreated()).andExpect(header().string("Location","/api/v1/merchant/orders/"+parcel)).andExpect(header().string("Cache-Control","no-store")).andExpect(jsonPath("$.id").value(parcel));
        verify(verifier).verify("test-only","orders:write");
    }
    @Test void listAndDetailRequireReadScope() throws Exception {
        when(service.list(any(),any())).thenReturn(new MerchantOrderPageResponse(List.of(),0,0,20));
        when(service.detail(any(),eq(parcel))).thenReturn(OrderTestData.response(merchant,parcel).order());
        mvc.perform(get("/api/v1/merchant/orders").header("X-API-Key","test-only")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/merchant/orders/"+parcel).header("X-API-Key","test-only")).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(parcel));
        verify(verifier,times(2)).verify("test-only","orders:read");
    }
    @Test void repeatedApiKeyAndMissingIdempotencyAreRejected() throws Exception {
        String request=new ObjectMapper().writeValueAsString(OrderTestData.request());
        mvc.perform(post("/api/v1/merchant/orders").header("X-API-Key","one","two").header("Idempotency-Key","key").contentType("application/json").content(request)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/merchant/orders").header("X-API-Key","test-only").contentType("application/json").content(request)).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
    @Test void clientCannotSupplyMerchantIdentityOrFees() throws Exception {
        mvc.perform(post("/api/v1/merchant/orders").header("X-API-Key","test-only").header("Idempotency-Key","key").contentType("application/json")
            .content("{\"merchantId\":\"forged\",\"totalFee\":0}")).andExpect(status().isBadRequest()); verifyNoInteractions(service);
    }
    @Test void invalidDateIs400() throws Exception {
        mvc.perform(get("/api/v1/merchant/orders").header("X-API-Key","test-only").param("createdFrom","invalid")).andExpect(status().isBadRequest());
    }
    @Test void storageFailureReturnsSanitized503() throws Exception {
        when(service.list(any(), any())).thenThrow(
                new org.springframework.dao.DataIntegrityViolationException("private database constraint detail"));
        mvc.perform(get("/api/v1/merchant/orders").header("X-API-Key", "test-only"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.code").value("ORDER_STORAGE_UNAVAILABLE"))
                .andExpect(jsonPath("$.message").value("Order storage is unavailable"));
    }
}
