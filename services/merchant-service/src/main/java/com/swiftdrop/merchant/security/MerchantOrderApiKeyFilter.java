package com.swiftdrop.merchant.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.merchant.exception.MerchantOrderException;
import com.swiftdrop.merchant.exception.MerchantOrderExceptionHandler.OrderErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.transaction.TransactionException;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

public final class MerchantOrderApiKeyFilter extends OncePerRequestFilter {
    public static final String AUTHORITY = "MERCHANT_ORDER_API_KEY";

    private final MerchantOrderAuthenticator authenticator;
    private final RequestMatcher write;
    private final ObjectMapper mapper;

    public MerchantOrderApiKeyFilter(MerchantOrderAuthenticator authenticator, RequestMatcher write,
            ObjectMapper mapper) {
        this.authenticator = authenticator;
        this.write = write;
        this.mapper = mapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        VerifiedMerchantOrderAccess access;
        try {
            access = authenticator.authenticate(Collections.list(request.getHeaders("X-API-Key")),
                    write.matches(request) ? "orders:write" : "orders:read");
        } catch (MerchantOrderException exception) {
            reject(response, exception);
            return;
        } catch (DataAccessException | TransactionException exception) {
            writeError(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                    new OrderErrorResponse("ORDER_STORAGE_UNAVAILABLE", "Order storage is unavailable"));
            return;
        }

        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                access, null, List.of(new SimpleGrantedAuthority(AUTHORITY))));
        SecurityContextHolder.setContext(context);
        try {
            chain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private void reject(HttpServletResponse response, MerchantOrderException exception) throws IOException {
        boolean forbidden = exception.getStatus() == HttpServletResponse.SC_FORBIDDEN;
        if (!forbidden) {
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "ApiKey");
        }
        var error = switch (forbidden ? exception.getCode() : "") {
            case "MERCHANT_NOT_APPROVED" -> new OrderErrorResponse("MERCHANT_NOT_APPROVED", "Merchant must be approved");
            case "INSUFFICIENT_SCOPE" -> new OrderErrorResponse("INSUFFICIENT_SCOPE", "Required credential scope unavailable");
            default -> forbidden
                    ? new OrderErrorResponse("ACCESS_DENIED", "Access denied")
                    : new OrderErrorResponse("INVALID_API_KEY", "API credential is invalid");
        };
        writeError(response, forbidden ? HttpServletResponse.SC_FORBIDDEN : HttpServletResponse.SC_UNAUTHORIZED, error);
    }

    private void writeError(HttpServletResponse response, int status, OrderErrorResponse error) throws IOException {
        response.setStatus(status);
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        mapper.writeValue(response.getOutputStream(), error);
    }
}
