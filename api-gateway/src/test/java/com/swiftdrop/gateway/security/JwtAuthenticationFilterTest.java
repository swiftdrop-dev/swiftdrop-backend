package com.swiftdrop.gateway.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.security.core.context.SecurityContextHolder;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtAuthenticationFilterTest {

    private static final String SECRET = "test-only-gateway-key-material-with-at-least-32-bytes";

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void clientIdentityHeadersDoNotAuthenticateRequest() throws Exception {
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(new JwtTokenValidator(SECRET));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-User-Id", "caller-chosen-id");
        request.addHeader("X-User-Roles", "ROLE_ADMIN");
        request.addHeader("X-User-Email", "attacker@example.test");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean();
        FilterChain chain = (req, res) -> chainCalled.set(true);

        filter.doFilter(request, response, chain);

        assertTrue(chainCalled.get());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void validBearerTokenCreatesAuthenticatedPrincipalAndRole() throws Exception {
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(new JwtTokenValidator(SECRET));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + JwtTokenValidatorTestToken.create(SECRET));
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        var authentication = SecurityContextHolder.getContext().getAuthentication();
        assertTrue(authentication.isAuthenticated());
        assertEquals("courier@example.test", authentication.getName());
        assertTrue(authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_COURIER")));
    }

    @Test
    void malformedSuppliedAuthorizationHeaderIsRejected() throws Exception {
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(new JwtTokenValidator(SECRET));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Basic something");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals(401, response.getStatus());
        assertFalse(SecurityContextHolder.getContext().getAuthentication() != null);
    }

    @Test
    void multipleAuthorizationHeaderValuesAreRejectedAsAmbiguous() throws Exception {
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(new JwtTokenValidator(SECRET));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + JwtTokenValidatorTestToken.create(SECRET));
        request.addHeader("Authorization", "Bearer another-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals(401, response.getStatus());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    /** Small test token helper keeps token construction out of production classes. */
    private static final class JwtTokenValidatorTestToken {
        static String create(String secret) {
            var key = io.jsonwebtoken.security.Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
            return io.jsonwebtoken.Jwts.builder()
                    .subject("courier@example.test")
                    .claim("role", "courier")
                    .issuedAt(new java.util.Date())
                    .expiration(new java.util.Date(System.currentTimeMillis() + 60_000))
                    .signWith(key)
                    .compact();
        }
    }
}
