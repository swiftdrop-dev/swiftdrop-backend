package com.swiftdrop.merchant.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.merchant.OrderTestData;
import com.swiftdrop.merchant.config.MerchantOrderSecurityConfig;
import com.swiftdrop.merchant.config.SecurityConfig;
import com.swiftdrop.merchant.controller.MerchantOrderController;
import com.swiftdrop.merchant.dto.response.MerchantOrderPageResponse;
import com.swiftdrop.merchant.entity.Merchant;
import com.swiftdrop.merchant.entity.MerchantApiCredential;
import com.swiftdrop.merchant.entity.enums.MerchantStatus;
import com.swiftdrop.merchant.repository.MerchantApiCredentialRepository;
import com.swiftdrop.merchant.service.MerchantOrderService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = MerchantOrderController.class, properties = {
        "merchant.persistence.enabled=true", "merchant.orders.enabled=true", "spring.flyway.enabled=false"})
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, MerchantOrderSecurityConfig.class,
        MerchantOrderAuthenticator.class, MerchantApiKeyVerifierImpl.class})
class MerchantOrderSecurityIntegrationTest {
    private static final String ORDERS = "/api/v1/merchant/orders";
    private static final String KEY = "test-only-order-credential";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @MockitoBean MerchantApiCredentialRepository repository;
    @MockitoBean MerchantOrderService service;
    @MockitoBean JwtTokenValidator jwtValidator;

    @Test
    void readUsesOnlyVerifiedMerchantIdentityForListAndDetail() throws Exception {
        var credential = storedCredential(" orders:read , tracking:read ");
        var actor = actor(credential, Set.of("orders:read", "tracking:read"));
        var parcel = OrderTestData.PARCEL_ID;
        when(service.list(eq(actor), any())).thenReturn(new MerchantOrderPageResponse(List.of(), 0, 0, 20));
        when(service.detail(actor, parcel)).thenReturn(OrderTestData.response(actor.credential().merchantId(), parcel).order());

        mvc.perform(get(ORDERS).header("X-API-Key", KEY).header("X-Merchant-Id", UUID.randomUUID())
                        .param("merchantId", UUID.randomUUID().toString()))
                .andExpect(status().isOk());
        mvc.perform(get(ORDERS + "/{id}", parcel).header("X-API-Key", KEY))
                .andExpect(status().isOk());
        mvc.perform(head(ORDERS).header("X-API-Key", KEY)).andExpect(status().isOk());
        verify(service, times(2)).list(eq(actor), any());
        verify(service).detail(actor, parcel);
        verify(repository, times(3)).findByKeyHash(digest());
    }

    @Test
    void writeScopeAllowsCreationWithoutJwt() throws Exception {
        var credential = storedCredential("orders:write");
        var actor = actor(credential, Set.of("orders:write"));
        var parcel = OrderTestData.PARCEL_ID;
        when(service.create(eq(actor), eq("request-1"), any()))
                .thenReturn(OrderTestData.response(actor.credential().merchantId(), parcel).order());

        mvc.perform(post(ORDERS).header("X-API-Key", KEY).header("Idempotency-Key", "request-1")
                        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(OrderTestData.request())))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, ORDERS + "/" + parcel));
        verify(service).create(eq(actor), eq("request-1"), any());
        verify(repository).findByKeyHash(digest());
    }

    @ParameterizedTest
    @ValueSource(strings = {"unknown", "inactive", "revoked", "expired"})
    void unusableCredentialsReturnSafe401(String state) throws Exception {
        if (!"unknown".equals(state)) {
            var credential = storedCredential("orders:read");
            switch (state) {
                case "inactive" -> credential.setIsActive(false);
                case "revoked" -> credential.setRevokedAt(Instant.now().minusSeconds(60));
                case "expired" -> credential.setExpiresAt(Instant.now().minusSeconds(60));
            }
        }
        mvc.perform(get(ORDERS).header("X-API-Key", KEY))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "ApiKey"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.code").value("INVALID_API_KEY"))
                .andExpect(content().string(not(containsString(KEY))));
        verifyNoInteractions(service);
    }

    @Test
    void validJwtAloneCannotAuthorizeOrders() throws Exception {
        when(jwtValidator.validate("test-jwt"))
                .thenReturn(new UserPrincipal(UUID.randomUUID(), "merchant@example.test", "MERCHANT"));

        mvc.perform(get(ORDERS).header(HttpHeaders.AUTHORIZATION, "Bearer test-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_API_KEY"));
        verifyNoInteractions(jwtValidator, repository, service);
    }

    @Test
    void duplicateApiKeysAreRejectedBeforeLookup() throws Exception {
        mvc.perform(get(ORDERS).header("X-API-Key", KEY, KEY))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(repository, service);
    }

    @Test
    void credentialStorageFailureReturnsSanitized503() throws Exception {
        when(repository.findByKeyHash(digest())).thenThrow(
                new org.springframework.dao.DataAccessResourceFailureException("private database detail"));
        mvc.perform(get(ORDERS).header("X-API-Key", KEY))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.code").value("ORDER_STORAGE_UNAVAILABLE"))
                .andExpect(jsonPath("$.message").value("Order storage is unavailable"));
        verifyNoInteractions(service);
    }

    @Test
    void readAndWriteScopesAreIndependentAndExact() throws Exception {
        var credential = storedCredential("orders:read, orders:write-extra");
        mvc.perform(post(ORDERS).header("X-API-Key", KEY))
                .andExpect(status().isForbidden())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_SCOPE"))
                .andExpect(content().string(not(containsString(KEY))));

        credential.setScopes("orders:write");
        mvc.perform(get(ORDERS + "/{id}", OrderTestData.PARCEL_ID).header("X-API-Key", KEY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_SCOPE"));
        verifyNoInteractions(service);
    }

    @Test
    void activeMerchantWithoutApprovedStatusIsForbidden() throws Exception {
        storedCredential("orders:read").getMerchant().setStatus(MerchantStatus.ACTIVE);
        mvc.perform(get(ORDERS).header("X-API-Key", KEY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MERCHANT_NOT_APPROVED"));
        verifyNoInteractions(service);
    }

    @Test
    void unmatchedOrderMethodsAndPluralMerchantRoutesKeepJwtDenyRules() throws Exception {
        when(jwtValidator.validate("test-jwt"))
                .thenReturn(new UserPrincipal(UUID.randomUUID(), "merchant@example.test", "MERCHANT"));
        mvc.perform(delete(ORDERS + "/{id}", OrderTestData.PARCEL_ID)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer test-jwt").header("X-API-Key", KEY))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/merchants/orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer test-jwt").header("X-API-Key", KEY))
                .andExpect(status().isForbidden());
        verify(jwtValidator, times(2)).validate("test-jwt");
        verifyNoInteractions(repository, service);
    }

    private MerchantApiCredential storedCredential(String scopes) throws Exception {
        var merchant = new Merchant();
        merchant.setId(UUID.randomUUID());
        merchant.setStatus(MerchantStatus.APPROVED);
        var credential = new MerchantApiCredential();
        credential.setId(UUID.randomUUID());
        credential.setMerchant(merchant);
        credential.setScopes(scopes);
        when(repository.findByKeyHash(digest())).thenReturn(Optional.of(credential));
        return credential;
    }

    private static VerifiedMerchantOrderAccess actor(MerchantApiCredential credential, Set<String> scopes) {
        return new VerifiedMerchantOrderAccess(
                new VerifiedMerchantApiCredential(credential.getMerchant().getId(), credential.getId(), scopes));
    }

    private static String digest() throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(KEY.getBytes(StandardCharsets.UTF_8)));
    }
}
