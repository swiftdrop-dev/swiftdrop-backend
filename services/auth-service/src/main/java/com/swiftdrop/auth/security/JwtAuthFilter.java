package com.swiftdrop.auth.security;

import com.swiftdrop.auth.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;

    public JwtAuthFilter(JwtUtil jwtUtil, UserRepository userRepository) {
        this.jwtUtil = jwtUtil;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");

        // Skip if no Bearer token is present
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        final String token = authHeader.substring(7);

        if (!jwtUtil.isTokenValid(token)) {
            filterChain.doFilter(request, response);
            return;
        }

        // Token is valid — populate the SecurityContext
        String userId = jwtUtil.extractUserId(token);
        String email = jwtUtil.extractEmail(token);
        String role  = jwtUtil.extractRole(token);

        // Account status enforcement: immediate revocation for deactivated or deleted accounts
        if (userId != null) {
            try {
                var userOpt = userRepository.findById(userId);
                if (userOpt.isEmpty() || !userOpt.get().isActive()) {
                    SecurityContextHolder.clearContext();
                    response.sendError(HttpServletResponse.SC_FORBIDDEN, "Account is deactivated or does not exist.");
                    return;
                }
            } catch (Exception e) {
                SecurityContextHolder.clearContext();
                response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "Authentication verification service temporarily unavailable.");
                return;
            }
        }

        var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + (role != null ? role.toUpperCase(Locale.ROOT) : "")));
        var authentication = new UsernamePasswordAuthenticationToken(userId != null ? userId : email, null, authorities);
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        filterChain.doFilter(request, response);
    }
}

