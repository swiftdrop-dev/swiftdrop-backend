package com.swiftdrop.gateway.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;

/** Optional Bearer authentication foundation; route authorization remains a separate decision. */
public final class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenValidator tokenValidator;

    public JwtAuthenticationFilter(JwtTokenValidator tokenValidator) {
        this.tokenValidator = tokenValidator;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {
        SecurityContextHolder.clearContext();
        Enumeration<String> authorizationValues = request.getHeaders(HttpHeaders.AUTHORIZATION);
        if (authorizationValues == null || !authorizationValues.hasMoreElements()) {
            filterChain.doFilter(request, response);
            return;
        }
        String authorization = authorizationValues.nextElement();
        if (authorizationValues.hasMoreElements()) {
            reject(response);
            return;
        }

        if (!authorization.startsWith("Bearer ") || authorization.length() <= 7
                || authorization.substring(7).isBlank()) {
            reject(response);
            return;
        }

        final AuthenticatedJwtPrincipal principal;
        try {
            principal = tokenValidator.validate(authorization.substring(7).trim());
        } catch (JwtTokenValidationException exception) {
            SecurityContextHolder.clearContext();
            reject(response);
            return;
        }

        var authentication = new UsernamePasswordAuthenticationToken(
                principal,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + principal.role().toUpperCase(Locale.ROOT))));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        filterChain.doFilter(request, response);
    }

    private void reject(HttpServletResponse response) throws IOException {
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid bearer token");
    }
}
