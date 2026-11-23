package com.swiftdrop.gateway.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Enumeration;
import java.util.Set;

@RestController
public class GatewayProxyController {

    private static final Logger log = LoggerFactory.getLogger(GatewayProxyController.class);
    private static final Set<String> FORWARDED_REQUEST_HEADERS = Set.of(
            HttpHeaders.AUTHORIZATION.toLowerCase(java.util.Locale.ROOT),
            HttpHeaders.ACCEPT.toLowerCase(java.util.Locale.ROOT),
            HttpHeaders.CONTENT_TYPE.toLowerCase(java.util.Locale.ROOT),
            HttpHeaders.IF_MATCH.toLowerCase(java.util.Locale.ROOT),
            HttpHeaders.IF_NONE_MATCH.toLowerCase(java.util.Locale.ROOT),
            HttpHeaders.IF_MODIFIED_SINCE.toLowerCase(java.util.Locale.ROOT),
            HttpHeaders.IF_UNMODIFIED_SINCE.toLowerCase(java.util.Locale.ROOT),
            "idempotency-key",
            "x-api-key"
    );

    private final RestClient restClient;
    private final String merchantServiceUrl;
    private final String dispatchServiceUrl;
    private final String authServiceUrl;
    private final String parcelServiceUrl;
    private final String courierServiceUrl;
    private final String pricingServiceUrl;
    private final String trackingServiceUrl;
    private final String notificationServiceUrl;
    private final String reportingServiceUrl;

    @org.springframework.beans.factory.annotation.Autowired
    public GatewayProxyController(
            @Value("${services.merchant.url:http://localhost:8084}") String merchantServiceUrl,
            @Value("${services.dispatch.url:http://localhost:8085}") String dispatchServiceUrl,
            @Value("${services.auth.url:http://localhost:8081}") String authServiceUrl,
            @Value("${services.parcel.url:http://localhost:8082}") String parcelServiceUrl,
            @Value("${services.courier.url:http://localhost:8086}") String courierServiceUrl,
            @Value("${services.pricing.url:http://localhost:8083}") String pricingServiceUrl,
            @Value("${services.tracking.url:http://localhost:8087}") String trackingServiceUrl,
            @Value("${services.notification.url:http://localhost:8088}") String notificationServiceUrl,
            @Value("${services.reporting.url:http://localhost:8090}") String reportingServiceUrl) {
        this(merchantServiceUrl, dispatchServiceUrl, authServiceUrl, parcelServiceUrl, courierServiceUrl, pricingServiceUrl, trackingServiceUrl, notificationServiceUrl, reportingServiceUrl, defaultRestClient());
    }

    GatewayProxyController(
            String merchantServiceUrl,
            String dispatchServiceUrl,
            String authServiceUrl,
            String parcelServiceUrl,
            String courierServiceUrl,
            String pricingServiceUrl) {
        this(merchantServiceUrl, dispatchServiceUrl, authServiceUrl, parcelServiceUrl, courierServiceUrl, pricingServiceUrl, "http://localhost:8087", "http://localhost:8088", "http://localhost:8090", defaultRestClient());
    }

    GatewayProxyController(
            String merchantServiceUrl,
            String dispatchServiceUrl,
            String authServiceUrl,
            String parcelServiceUrl,
            String courierServiceUrl,
            RestClient restClient) {
        this(merchantServiceUrl, dispatchServiceUrl, authServiceUrl, parcelServiceUrl, courierServiceUrl, "http://localhost:8083", "http://localhost:8087", "http://localhost:8088", "http://localhost:8090", restClient);
    }

    GatewayProxyController(
            String merchantServiceUrl,
            String dispatchServiceUrl,
            String authServiceUrl,
            String parcelServiceUrl,
            String courierServiceUrl,
            String pricingServiceUrl,
            RestClient restClient) {
        this(merchantServiceUrl, dispatchServiceUrl, authServiceUrl, parcelServiceUrl, courierServiceUrl, pricingServiceUrl, "http://localhost:8087", "http://localhost:8088", "http://localhost:8090", restClient);
    }

    GatewayProxyController(
            String merchantServiceUrl,
            String dispatchServiceUrl,
            String authServiceUrl,
            String parcelServiceUrl,
            String courierServiceUrl,
            String pricingServiceUrl,
            String trackingServiceUrl,
            RestClient restClient) {
        this(merchantServiceUrl, dispatchServiceUrl, authServiceUrl, parcelServiceUrl, courierServiceUrl, pricingServiceUrl, trackingServiceUrl, "http://localhost:8088", "http://localhost:8090", restClient);
    }

    GatewayProxyController(
            String merchantServiceUrl,
            String dispatchServiceUrl,
            String authServiceUrl,
            String parcelServiceUrl,
            String courierServiceUrl,
            String pricingServiceUrl,
            String trackingServiceUrl,
            String notificationServiceUrl,
            RestClient restClient) {
        this(merchantServiceUrl, dispatchServiceUrl, authServiceUrl, parcelServiceUrl, courierServiceUrl, pricingServiceUrl, trackingServiceUrl, notificationServiceUrl, "http://localhost:8090", restClient);
    }

    GatewayProxyController(
            String merchantServiceUrl,
            String dispatchServiceUrl,
            String authServiceUrl,
            String parcelServiceUrl,
            String courierServiceUrl,
            String pricingServiceUrl,
            String trackingServiceUrl,
            String notificationServiceUrl,
            String reportingServiceUrl,
            RestClient restClient) {
        this.restClient = restClient;
        this.merchantServiceUrl = merchantServiceUrl;
        this.dispatchServiceUrl = dispatchServiceUrl;
        this.authServiceUrl = authServiceUrl;
        this.parcelServiceUrl = parcelServiceUrl;
        this.courierServiceUrl = courierServiceUrl;
        this.pricingServiceUrl = pricingServiceUrl;
        this.trackingServiceUrl = trackingServiceUrl;
        this.notificationServiceUrl = notificationServiceUrl;
        this.reportingServiceUrl = reportingServiceUrl;
    }

    private static RestClient defaultRestClient() {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(30));
        return RestClient.builder().requestFactory(factory).build();
    }

    @RequestMapping(value = {
            "/api/v1/merchants/**",
            "/api/v1/merchant/**",
            "/api/v1/dispatch/**",
            "/api/v1/auth/**",
            "/api/v1/parcels/**",
            "/api/v1/couriers/**",
            "/api/v1/pricing/**",
            "/api/v1/zones/**",
            "/api/v1/receipts/**",
            "/api/v1/tracking/**",
            "/api/v1/notifications/**",
            "/api/v1/reports/**"
    },
            method = {org.springframework.web.bind.annotation.RequestMethod.GET,
                    org.springframework.web.bind.annotation.RequestMethod.POST,
                    org.springframework.web.bind.annotation.RequestMethod.PUT,
                    org.springframework.web.bind.annotation.RequestMethod.PATCH,
                    org.springframework.web.bind.annotation.RequestMethod.DELETE})
    public ResponseEntity<byte[]> proxyRequest(
            HttpMethod method,
            HttpServletRequest request,
            @RequestBody(required = false) byte[] body) {

        String path = request.getRequestURI();
        String query = request.getQueryString();

        ResponseEntity<byte[]> authCheck = checkAuthorization(path);
        if (authCheck != null) {
            return authCheck;
        }

        String targetBaseUrl;

        if (path.equals("/api/v1/merchants") || path.startsWith("/api/v1/merchants/")
                || path.equals("/api/v1/merchant") || path.startsWith("/api/v1/merchant/")) {
            targetBaseUrl = merchantServiceUrl;
        } else if (path.equals("/api/v1/dispatch") || path.startsWith("/api/v1/dispatch/")) {
            targetBaseUrl = dispatchServiceUrl;
        } else if (path.equals("/api/v1/auth") || path.startsWith("/api/v1/auth/")) {
            targetBaseUrl = authServiceUrl;
        } else if (path.equals("/api/v1/parcels") || path.startsWith("/api/v1/parcels/")) {
            targetBaseUrl = parcelServiceUrl;
        } else if (path.equals("/api/v1/couriers") || path.startsWith("/api/v1/couriers/")) {
            targetBaseUrl = courierServiceUrl;
        } else if (path.equals("/api/v1/pricing") || path.startsWith("/api/v1/pricing/")
                || path.equals("/api/v1/zones") || path.startsWith("/api/v1/zones/")
                || path.equals("/api/v1/receipts") || path.startsWith("/api/v1/receipts/")) {
            targetBaseUrl = pricingServiceUrl;
        } else if (path.equals("/api/v1/tracking") || path.startsWith("/api/v1/tracking/")) {
            targetBaseUrl = trackingServiceUrl;
        } else if (path.equals("/api/v1/notifications") || path.startsWith("/api/v1/notifications/")) {
            targetBaseUrl = notificationServiceUrl;
        } else if (path.equals("/api/v1/reports") || path.startsWith("/api/v1/reports/")) {
            targetBaseUrl = reportingServiceUrl;
        } else {
            return ResponseEntity.notFound().build();
        }

        String targetUrl = targetBaseUrl + path + (query != null ? "?" + query : "");
        log.debug("Proxying {} {}", method, path);

        byte[] payload = (body != null && body.length > 0) ? body : null;
        if (payload == null) {
            try {
                byte[] raw = request.getInputStream().readAllBytes();
                if (raw != null && raw.length > 0) {
                    payload = raw;
                }
            } catch (Exception ignored) {
            }
        }
        final byte[] finalPayload = payload != null ? payload : new byte[0];

        try {
            return restClient.method(method)
                    .uri(URI.create(targetUrl))
                    .headers(httpHeaders -> copyHeaders(request, httpHeaders))
                    .body(finalPayload)
                    .exchange((clientRequest, clientResponse) -> {
                        HttpHeaders responseHeaders = new HttpHeaders();
                        clientResponse.getHeaders().forEach((name, values) -> {
                            if (!HttpHeaders.TRANSFER_ENCODING.equalsIgnoreCase(name)) {
                                responseHeaders.put(name, values);
                            }
                        });
                        return ResponseEntity.status(clientResponse.getStatusCode())
                                .headers(responseHeaders)
                                .body(clientResponse.bodyTo(byte[].class));
                    });
        } catch (RestClientResponseException ex) {
            HttpHeaders errorHeaders = new HttpHeaders();
            if (ex.getResponseHeaders() != null) {
                ex.getResponseHeaders().forEach((name, values) -> {
                    if (!HttpHeaders.TRANSFER_ENCODING.equalsIgnoreCase(name)) {
                        errorHeaders.put(name, values);
                    }
                });
            }
            return ResponseEntity.status(ex.getStatusCode())
                    .headers(errorHeaders)
                    .body(ex.getResponseBodyAsByteArray());
        } catch (ResourceAccessException ex) {
            boolean isTimeout = ex.getCause() instanceof SocketTimeoutException
                    || (ex.getCause() != null && ex.getCause().getClass().getSimpleName().contains("Timeout"))
                    || (ex.getMessage() != null && ex.getMessage().toLowerCase(java.util.Locale.ROOT).contains("timeout"));
            int status = isTimeout ? 504 : 503;
            String code = isTimeout ? "GATEWAY_TIMEOUT" : "DOWNSTREAM_SERVICE_UNAVAILABLE";
            String msg = isTimeout ? "Downstream service timed out" : "Downstream service unavailable";
            log.warn("Downstream communication failure [method={}, path={}, status={}, code={}]", method, path, status, code);
            return ResponseEntity.status(status)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(("{\"success\":false,\"error\":{\"code\":\"" + code + "\",\"message\":\"" + msg + "\"}}")
                            .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (Exception ex) {
            log.warn("Proxy request failed [method={}, path={}, errorType={}]", method, path,
                    ex.getClass().getSimpleName());
            return ResponseEntity.status(503)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{\"success\":false,\"error\":{\"code\":\"DOWNSTREAM_SERVICE_UNAVAILABLE\",\"message\":\"Downstream service unavailable\"}}"
                            .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
    }

    void copyHeaders(HttpServletRequest request, HttpHeaders headers) {
        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames != null && headerNames.hasMoreElements()) {
            String name = headerNames.nextElement();
            if (name == null || !FORWARDED_REQUEST_HEADERS.contains(name.toLowerCase(java.util.Locale.ROOT))) {
                continue;
            }
            Enumeration<String> values = request.getHeaders(name);
            while (values.hasMoreElements()) {
                headers.add(name, values.nextElement());
            }
        }

        // Strip any client-supplied user identity headers to prevent spoofing
        headers.remove("X-User-Id");
        headers.remove("x-user-id");
        headers.remove("X-User-Roles");
        headers.remove("x-user-roles");
        headers.remove("X-User-Email");
        headers.remove("x-user-email");

        org.springframework.security.core.Authentication auth =
                org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof com.swiftdrop.gateway.security.AuthenticatedJwtPrincipal principal) {
            headers.set("X-User-Id", principal.subject());
            headers.set("X-User-Roles", principal.role());
            if (principal.email() != null && !principal.email().isBlank()) {
                headers.set("X-User-Email", principal.email());
            }
        }
    }

    private ResponseEntity<byte[]> checkAuthorization(String path) {
        boolean isAdminOnly = path.startsWith("/api/v1/auth/admin")
                || path.startsWith("/api/v1/merchants/admin")
                || path.startsWith("/api/v1/zones/admin")
                || path.equals("/api/v1/reports/merchant-volume")
                || path.startsWith("/api/v1/reports/merchant-volume/");
        boolean isPrivileged = isAdminOnly
                || path.startsWith("/api/v1/parcels/admin")
                || path.startsWith("/api/v1/reports");

        if (!isPrivileged) {
            return null;
        }

        org.springframework.security.core.Authentication auth =
                org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();

        if (auth == null || !(auth.getPrincipal() instanceof com.swiftdrop.gateway.security.AuthenticatedJwtPrincipal principal)) {
            return ResponseEntity.status(401)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{\"success\":false,\"error\":{\"code\":\"UNAUTHORIZED\",\"message\":\"Authentication required\"}}"
                            .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }

        String role = principal.role() != null ? principal.role().toUpperCase(java.util.Locale.ROOT) : "";
        boolean hasAdmin = role.contains("ADMIN");
        boolean hasDispatcher = role.contains("DISPATCHER") || role.contains("STAFF");

        if (isAdminOnly && !hasAdmin) {
            return ResponseEntity.status(403)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{\"success\":false,\"error\":{\"code\":\"FORBIDDEN\",\"message\":\"Admin role required\"}}"
                            .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }

        if (!hasAdmin && !hasDispatcher) {
            return ResponseEntity.status(403)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{\"success\":false,\"error\":{\"code\":\"FORBIDDEN\",\"message\":\"Admin or Dispatcher role required\"}}"
                            .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }

        return null;
    }
}
