package com.swiftdrop.gateway.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class GatewayProxyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldHandleProxyRouteWhenDownstreamOffline() throws Exception {
        // When downstream merchant-service is offline, gateway should return 503 DOWNSTREAM_SERVICE_UNAVAILABLE
        mockMvc.perform(get("/api/v1/merchants/onboarding/test-id")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void shouldHandleDispatchProxyRouteWhenDownstreamOffline() throws Exception {
        // When downstream dispatch-service is offline, gateway should return 503 DOWNSTREAM_SERVICE_UNAVAILABLE
        mockMvc.perform(get("/api/v1/dispatch/jobs/unassigned")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void shouldHandleAuthLoginProxyRouteWhenDownstreamOffline() throws Exception {
        // When downstream auth-service is offline, gateway should return 503 DOWNSTREAM_SERVICE_UNAVAILABLE
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user@example.test\",\"password\":\"secret123\"}"))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void shouldHandleAuthRegisterProxyRouteWhenDownstreamOffline() throws Exception {
        // When downstream auth-service is offline, gateway should return 503 DOWNSTREAM_SERVICE_UNAVAILABLE
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Test User\",\"email\":\"user@example.test\",\"password\":\"secret123\"}"))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void shouldHandleParcelProxyRouteWhenDownstreamOffline() throws Exception {
        mockMvc.perform(get("/api/v1/parcels/SWF-12345")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void shouldHandleCourierProxyRouteWhenDownstreamOffline() throws Exception {
        mockMvc.perform(get("/api/v1/couriers/cou_001")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void invalidBearerTokenIsRejectedBeforeProxying() throws Exception {
        mockMvc.perform(get("/api/v1/merchants/onboarding/test-id")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }
}
