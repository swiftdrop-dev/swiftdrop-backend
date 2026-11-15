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
        request.addHeader("X-Forwarded-For", "127.0.0.1");
        request.addHeader("Forwarded", "for=127.0.0.1;proto=https");
        request.addHeader("Cookie", "session=attacker-controlled");
        request.addHeader("X-Internal-Role", "admin");
        request.addHeader("Idempotency-Key", "request-key-123");
        request.addHeader(HttpHeaders.ACCEPT, "application/json");
        HttpHeaders forwardedHeaders = new HttpHeaders();

        controller.copyHeaders(request, forwardedHeaders);

        assertEquals("Bearer test-token", forwardedHeaders.getFirst(HttpHeaders.AUTHORIZATION));
        assertNull(forwardedHeaders.getFirst("X-User-Id"));
        assertNull(forwardedHeaders.getFirst("X-User-Roles"));
        assertNull(forwardedHeaders.getFirst("X-User-Email"));
        assertNull(forwardedHeaders.getFirst("X-Forwarded-For"));
        assertNull(forwardedHeaders.getFirst("Forwarded"));
        assertNull(forwardedHeaders.getFirst("Cookie"));
        assertNull(forwardedHeaders.getFirst("X-Internal-Role"));
        assertEquals("request-key-123", forwardedHeaders.getFirst("Idempotency-Key"));
        assertEquals("application/json", forwardedHeaders.getFirst(HttpHeaders.ACCEPT));
    }
}
