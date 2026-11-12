package com.swiftdrop.webhook.subscription;

import java.io.IOException;
import java.util.Set;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Local browser guard, NOT production authentication. */
@Component
@Profile("local-webhooks")
public class LocalWebhookAccess extends OncePerRequestFilter {
    private static final Set<String> ORIGINS = Set.of(
            "http://localhost:5173", "http://localhost:5174",
            "http://127.0.0.1:5173", "http://127.0.0.1:5174");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!request.getRequestURI().startsWith("/api/v1/merchant/webhooks")) {
            chain.doFilter(request, response);
            return;
        }
        String origin = request.getHeader("Origin");
        if (origin != null && !ORIGINS.contains(origin)) {
            response.sendError(403); return;
        }
        if (origin != null) {
            response.setHeader("Access-Control-Allow-Origin", origin);
            response.setHeader("Vary", "Origin");
            response.setHeader("Access-Control-Allow-Methods", "GET,POST,PATCH,DELETE,OPTIONS");
            response.setHeader("Access-Control-Allow-Headers", "Content-Type,X-SwiftDrop-Local");
        }
        if ("OPTIONS".equals(request.getMethod())) { response.setStatus(204); return; }
        if (!"true".equals(request.getHeader("X-SwiftDrop-Local"))) {
            response.sendError(403); return;
        }
        chain.doFilter(request, response);
    }
}
