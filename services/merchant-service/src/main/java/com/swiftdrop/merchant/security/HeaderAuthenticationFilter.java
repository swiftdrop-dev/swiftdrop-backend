package com.swiftdrop.merchant.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class HeaderAuthenticationFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String userIdHeader = request.getHeader("X-User-Id");
        String userRolesHeader = request.getHeader("X-User-Roles");
        String userEmailHeader = request.getHeader("X-User-Email");

        if (userIdHeader != null && !userIdHeader.isBlank()) {
            try {
                UUID userId = UUID.fromString(userIdHeader.trim());
                List<String> roles;
                if (userRolesHeader != null && !userRolesHeader.isBlank()) {
                    roles = Arrays.stream(userRolesHeader.split(","))
                            .map(String::trim)
                            .filter(r -> !r.isEmpty())
                            .collect(Collectors.toList());
                } else {
                    roles = Collections.singletonList("ROLE_MERCHANT");
                }

                UserPrincipal principal = new UserPrincipal(userId, userEmailHeader, roles);
                UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                        principal, null, principal.getAuthorities()
                );
                SecurityContextHolder.getContext().setAuthentication(auth);
            } catch (IllegalArgumentException ignored) {
                // Ignore malformed UUID in header
            }
        }

        filterChain.doFilter(request, response);
    }
}
