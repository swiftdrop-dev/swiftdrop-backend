package com.swiftdrop.gateway.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class GatewayProxyHeaderSanitizationTest {

    @Test
    void stripsCallerSuppliedIdentityHeadersButPreservesBearerToken() {
        GatewayProxyController controller = new GatewayProxyController("http://merchant.test", "http://dispatch.test");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer test-token");
        request.addHeader("X-User-Id", "attacker-id");
        request.addHeader("X-User-Roles", "ROLE_ADMIN");
        request.addHeader("X-User-Email", "attacker@example.test");
        HttpHeaders forwardedHeaders = new HttpHeaders();

        controller.copyHeaders(request, forwardedHeaders);

        assertEquals("Bearer test-token", forwardedHeaders.getFirst(HttpHeaders.AUTHORIZATION));
        assertNull(forwardedHeaders.getFirst("X-User-Id"));
        assertNull(forwardedHeaders.getFirst("X-User-Roles"));
        assertNull(forwardedHeaders.getFirst("X-User-Email"));
    }
}
