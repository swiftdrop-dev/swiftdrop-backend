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

@RestController
public class GatewayProxyController {

    private static final Logger log = LoggerFactory.getLogger(GatewayProxyController.class);

    private final RestClient restClient;
    private final String merchantServiceUrl;
    private final String dispatchServiceUrl;

    public GatewayProxyController(
            @Value("${services.merchant.url:http://localhost:8084}") String merchantServiceUrl,
            @Value("${services.dispatch.url:http://localhost:8085}") String dispatchServiceUrl) {
        this.restClient = RestClient.builder().build();
        this.merchantServiceUrl = merchantServiceUrl;
        this.dispatchServiceUrl = dispatchServiceUrl;
    }

    @RequestMapping(value = {"/api/v1/merchants/**", "/api/v1/dispatch/**"})
    public ResponseEntity<byte[]> proxyRequest(
            HttpMethod method,
            HttpServletRequest request,
            @RequestBody(required = false) byte[] body) {

        String path = request.getRequestURI();
        String query = request.getQueryString();
        String targetBaseUrl;

        if (path.startsWith("/api/v1/merchants")) {
            targetBaseUrl = merchantServiceUrl;
        } else if (path.startsWith("/api/v1/dispatch")) {
            targetBaseUrl = dispatchServiceUrl;
        } else {
            return ResponseEntity.notFound().build();
        }

        String targetUrl = targetBaseUrl + path + (query != null ? "?" + query : "");
        log.debug("Proxying {} {} -> {}", method, path, targetUrl);

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
            log.error("Proxy error forwarding to {}: {}", targetUrl, ex.getMessage());
            return ResponseEntity.status(503)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(("{\"success\":false,\"error\":{\"code\":\"DOWNSTREAM_SERVICE_UNAVAILABLE\",\"message\":\"Downstream service unavailable: "
                            + ex.getMessage().replace("\"", "\\\"") + "\"}}").getBytes());
        }
    }

    private void copyHeaders(HttpServletRequest request, HttpHeaders headers) {
        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames != null && headerNames.hasMoreElements()) {
            String name = headerNames.nextElement();
            if (name.equalsIgnoreCase(HttpHeaders.HOST) ||
                name.equalsIgnoreCase(HttpHeaders.CONTENT_LENGTH)) {
                continue;
            }
            Enumeration<String> values = request.getHeaders(name);
            while (values.hasMoreElements()) {
                headers.add(name, values.nextElement());
            }
        }
    }
}
