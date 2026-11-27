package com.swiftdrop.courier.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Enumeration;

/**
 * Authenticates the dispatch service from a configured shared credential.
 * Caller-supplied user ids and roles are ignored.
 */
public class DispatchServiceAuthenticationFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Dispatch-Service-Token";

    private final String configuredToken;

    public DispatchServiceAuthenticationFilter(String configuredToken) {
        this.configuredToken = configuredToken == null ? "" : configuredToken;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Enumeration<String> presented = request.getHeaders(HEADER);
        if (presented == null || !presented.hasMoreElements()) {
            chain.doFilter(request, response);
            return;
        }
        String token = presented.nextElement();
        if (presented.hasMoreElements() || !matches(configuredToken, token)) {
            SecurityContextHolder.clearContext();
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
            response.sendError(HttpStatus.UNAUTHORIZED.value(), "Invalid service credential");
            return;
        }
        DispatchServicePrincipal principal = new DispatchServicePrincipal();
        var authentication = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authentication);
        chain.doFilter(request, response);
    }

    static boolean matches(String configured, String presented) {
        if (configured == null || configured.isBlank() || presented == null || presented.isBlank()) {
            return false;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] expected = digest.digest(configured.getBytes(StandardCharsets.UTF_8));
            byte[] actual = digest.digest(presented.getBytes(StandardCharsets.UTF_8));
            return MessageDigest.isEqual(expected, actual);
        } catch (NoSuchAlgorithmException exception) {
            return false;
        }
    }
}
