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
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.URI;
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
            "idempotency-key"
    );

    private final RestClient restClient;
    private final String merchantServiceUrl;
    private final String dispatchServiceUrl;
    private final String authServiceUrl;
    private final String parcelServiceUrl;
    private final String courierServiceUrl;
    private final String pricingServiceUrl;

    public GatewayProxyController(
            @Value("${services.merchant.url:http://localhost:8084}") String merchantServiceUrl,
            @Value("${services.dispatch.url:http://localhost:8085}") String dispatchServiceUrl,
            @Value("${services.auth.url:http://localhost:8081}") String authServiceUrl,
            @Value("${services.parcel.url:http://localhost:8082}") String parcelServiceUrl,
            @Value("${services.courier.url:http://localhost:8086}") String courierServiceUrl,
            @Value("${services.pricing.url:http://localhost:8083}") String pricingServiceUrl) {
        this.restClient = RestClient.builder().build();
        this.merchantServiceUrl = merchantServiceUrl;
        this.dispatchServiceUrl = dispatchServiceUrl;
        this.authServiceUrl = authServiceUrl;
        this.parcelServiceUrl = parcelServiceUrl;
        this.courierServiceUrl = courierServiceUrl;
        this.pricingServiceUrl = pricingServiceUrl;
    }

    @RequestMapping(value = {
            "/api/v1/merchants/**",
            "/api/v1/dispatch/**",
            "/api/v1/auth/**",
            "/api/v1/parcels/**",
            "/api/v1/couriers/**",
            "/api/v1/pricing/**",
            "/api/v1/zones/**",
            "/api/v1/receipts/**"
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
        String targetBaseUrl;

        if (path.equals("/api/v1/merchants") || path.startsWith("/api/v1/merchants/")) {
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
        } else {
            return ResponseEntity.notFound().build();
        }

        String targetUrl = targetBaseUrl + path + (query != null ? "?" + query : "");
        log.debug("Proxying {} {}", method, path);

        try {
            return restClient.method(method)
                    .uri(URI.create(targetUrl))
                    .headers(httpHeaders -> copyHeaders(request, httpHeaders))
                    .body(body != null ? body : new byte[0])
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
    }
}
