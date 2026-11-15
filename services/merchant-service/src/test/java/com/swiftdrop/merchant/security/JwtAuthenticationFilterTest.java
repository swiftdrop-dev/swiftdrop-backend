package com.swiftdrop.merchant.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtAuthenticationFilterTest {

    private static final String TEST_SECRET = "test-only-merchant-jwt-key-material-at-least-32-bytes";

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void callerIdentityHeadersDoNotCreateAuthentication() throws Exception {
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(new JwtTokenValidator(TEST_SECRET));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-User-Id", "caller-selected-id");
        request.addHeader("X-User-Roles", "ROLE_MERCHANT");
        request.addHeader("X-User-Email", "attacker@example.test");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean continued = new AtomicBoolean();
        FilterChain chain = (req, res) -> continued.set(true);

        filter.doFilter(request, response, chain);

        assertTrue(continued.get());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void ambiguousAuthorizationHeadersAreRejected() throws Exception {
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(new JwtTokenValidator(TEST_SECRET));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer first");
        request.addHeader("Authorization", "Bearer second");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> { });

        assertEquals(401, response.getStatus());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }
}
