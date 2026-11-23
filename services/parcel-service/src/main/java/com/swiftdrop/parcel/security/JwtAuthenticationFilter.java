package com.swiftdrop.parcel.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Enumeration;

public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenValidator validator;

    public JwtAuthenticationFilter(JwtTokenValidator validator) {
        this.validator = validator;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Enumeration<String> authorizationValues = request.getHeaders(HttpHeaders.AUTHORIZATION);
        if (authorizationValues != null && authorizationValues.hasMoreElements()) {
            String header = authorizationValues.nextElement();
            if (authorizationValues.hasMoreElements() || header == null || !header.startsWith("Bearer ")
                    || header.length() == 7 || header.substring(7).isBlank()) {
                reject(response);
                return;
            }

            try {
                UserPrincipal principal = validator.validate(header.substring(7));
                var authentication = new UsernamePasswordAuthenticationToken(
                        principal, null, principal.getAuthorities());
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (JwtTokenValidationException exception) {
                SecurityContextHolder.clearContext();
                reject(response);
                return;
            }
        }
        // X-User-* headers are deliberately ignored and never establish identity.
        chain.doFilter(request, response);
    }

    private void reject(HttpServletResponse response) throws IOException {
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        response.sendError(HttpStatus.UNAUTHORIZED.value(), "Invalid bearer token");
    }
}
