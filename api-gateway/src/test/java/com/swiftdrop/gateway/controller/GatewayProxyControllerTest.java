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
                        .header(HttpHeaders.AUTHORIZATION, createTestBearer("disp@example.test", "dispatcher"))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void courierDirectoryRequiresDispatcherOrAdmin() throws Exception {
        mockMvc.perform(get("/api/v1/couriers")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/couriers")
                        .header(HttpHeaders.AUTHORIZATION, createTestBearer("cust@example.test", "customer")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/couriers")
                        .header(HttpHeaders.AUTHORIZATION, createTestBearer("courier@example.test", "courier")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/couriers/me")
                        .header(HttpHeaders.AUTHORIZATION, createTestBearer("courier@example.test", "courier")))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void invalidBearerTokenIsRejectedBeforeProxying() throws Exception {
        mockMvc.perform(get("/api/v1/merchants/onboarding/test-id")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldReturnGatewayTimeoutWhenDownstreamTimesOut() {
        org.springframework.web.client.RestClient mockRestClient = org.mockito.Mockito.mock(org.springframework.web.client.RestClient.class);
        org.springframework.web.client.RestClient.RequestBodyUriSpec uriSpec = org.mockito.Mockito.mock(org.springframework.web.client.RestClient.RequestBodyUriSpec.class);
        org.mockito.Mockito.when(mockRestClient.method(org.springframework.http.HttpMethod.GET)).thenReturn(uriSpec);
        org.mockito.Mockito.when(uriSpec.uri(org.mockito.ArgumentMatchers.any(java.net.URI.class))).thenReturn(uriSpec);
        org.mockito.Mockito.when(uriSpec.headers(org.mockito.ArgumentMatchers.any())).thenReturn(uriSpec);
        org.mockito.Mockito.when(uriSpec.body(org.mockito.ArgumentMatchers.any(byte[].class))).thenReturn(uriSpec);
        org.mockito.Mockito.when(uriSpec.exchange(org.mockito.ArgumentMatchers.any())).thenThrow(
                new org.springframework.web.client.ResourceAccessException("Read timed out", new java.net.SocketTimeoutException("Read timed out")));

        GatewayProxyController controller = new GatewayProxyController(
                "http://localhost:8084", "http://localhost:8085", "http://localhost:8081",
                "http://localhost:8082", "http://localhost:8086", mockRestClient);

        org.springframework.mock.web.MockHttpServletRequest request = new org.springframework.mock.web.MockHttpServletRequest("GET", "/api/v1/merchants/test");
        org.springframework.http.ResponseEntity<byte[]> response = controller.proxyRequest(org.springframework.http.HttpMethod.GET, request, null);

        org.junit.jupiter.api.Assertions.assertEquals(504, response.getStatusCode().value());
        org.junit.jupiter.api.Assertions.assertTrue(new String(response.getBody()).contains("GATEWAY_TIMEOUT"));
    }

    @Test
    void unauthenticatedAdminRouteReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/auth/admin/users")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void nonAdminRoleForbiddenOnAdminOnlyRoute() throws Exception {
        mockMvc.perform(get("/api/v1/auth/admin/users")
                        .header(HttpHeaders.AUTHORIZATION, createTestBearer("customer@example.test", "customer"))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    void dispatcherForbiddenOnAdminOnlyMerchantRoute() throws Exception {
        mockMvc.perform(get("/api/v1/merchants/admin/pending")
                        .header(HttpHeaders.AUTHORIZATION, createTestBearer("disp@example.test", "dispatcher"))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    void dispatcherPermittedOnReportsRoute() throws Exception {
        // Dispatcher role is permitted through gateway; downstream offline causes 503
        mockMvc.perform(get("/api/v1/reports/dashboard-kpis")
                        .header(HttpHeaders.AUTHORIZATION, createTestBearer("disp@example.test", "dispatcher"))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void customerForbiddenOnAdministrativeParcelRoute() throws Exception {
        mockMvc.perform(get("/api/v1/parcels/admin/summary")
                        .header(HttpHeaders.AUTHORIZATION, createTestBearer("cust@example.test", "customer"))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    void courierForbiddenOnReportingRoute() throws Exception {
        mockMvc.perform(get("/api/v1/reports/dashboard-kpis")
                        .header(HttpHeaders.AUTHORIZATION, createTestBearer("courier@example.test", "courier"))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    void dispatcherForbiddenOnAdminOnlyMerchantVolumeReport() throws Exception {
        mockMvc.perform(get("/api/v1/reports/merchant-volume")
                        .header(HttpHeaders.AUTHORIZATION, createTestBearer("disp@example.test", "dispatcher"))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminPermittedOnMerchantVolumeReport() throws Exception {
        mockMvc.perform(get("/api/v1/reports/merchant-volume")
                        .header(HttpHeaders.AUTHORIZATION, createTestBearer("admin@example.test", "admin"))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void adminPermittedOnAdminRoute() throws Exception {
        // Admin role is permitted through gateway; downstream offline causes 503
        mockMvc.perform(get("/api/v1/auth/admin/users")
                        .header(HttpHeaders.AUTHORIZATION, createTestBearer("admin@example.test", "admin"))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isServiceUnavailable());
    }

    private static String createTestBearer(String subject, String role) {
        var key = io.jsonwebtoken.security.Keys.hmacShaKeyFor(
                "test-only-gateway-jwt-key-material-at-least-32-bytes".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        java.util.Date now = new java.util.Date();
        return "Bearer " + io.jsonwebtoken.Jwts.builder()
                .subject(subject)
                .claim("role", role)
                .issuedAt(now)
                .expiration(new java.util.Date(now.getTime() + 300_000))
                .signWith(key)
                .compact();
    }
}
