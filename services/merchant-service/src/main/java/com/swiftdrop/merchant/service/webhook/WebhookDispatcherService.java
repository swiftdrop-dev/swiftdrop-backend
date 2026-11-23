package com.swiftdrop.merchant.service.webhook;

import com.swiftdrop.merchant.entity.WebhookDelivery;
import com.swiftdrop.merchant.entity.WebhookDeliveryAttempt;
import com.swiftdrop.merchant.entity.WebhookEndpoint;
import com.swiftdrop.merchant.entity.enums.WebhookDeliveryStatus;
import com.swiftdrop.merchant.repository.WebhookDeliveryAttemptRepository;
import com.swiftdrop.merchant.repository.WebhookDeliveryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Service
public class WebhookDispatcherService {

    private static final Logger log = LoggerFactory.getLogger(WebhookDispatcherService.class);
    private static final int MAX_RESPONSE_LENGTH = 1000;

    // Exponential backoff intervals in seconds: 30s, 120s (2m), 600s (10m), 1800s (30m), 3600s (1h)
    private static final long[] BACKOFF_SECONDS = {30L, 120L, 600L, 1800L, 3600L};

    private final WebhookDeliveryRepository deliveryRepository;
    private final WebhookDeliveryAttemptRepository attemptRepository;
    private final WebhookSignatureService signatureService;
    private final WebhookSecretCryptoService cryptoService;
    private final WebhookSecurityValidator securityValidator;
    private final HttpClient httpClient;
    private final Duration requestTimeout;

    public WebhookDispatcherService(
            WebhookDeliveryRepository deliveryRepository,
            WebhookDeliveryAttemptRepository attemptRepository,
            WebhookSignatureService signatureService,
            WebhookSecretCryptoService cryptoService,
            WebhookSecurityValidator securityValidator,
            @Value("${merchant.webhook.timeout-seconds:10}") int timeoutSeconds) {
        this.deliveryRepository = deliveryRepository;
        this.attemptRepository = attemptRepository;
        this.signatureService = signatureService;
        this.cryptoService = cryptoService;
        this.securityValidator = securityValidator;
        this.requestTimeout = Duration.ofSeconds(timeoutSeconds);
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean claimForDispatch(UUID deliveryId, Collection<WebhookDeliveryStatus> eligibleStatuses) {
        int updated = deliveryRepository.claimDelivery(
                deliveryId,
                WebhookDeliveryStatus.PROCESSING,
                Instant.now(),
                eligibleStatuses
        );
        return updated > 0;
    }

    @Transactional
    public WebhookDelivery dispatch(UUID deliveryId) {
        boolean claimed = claimForDispatch(deliveryId, List.of(WebhookDeliveryStatus.PENDING, WebhookDeliveryStatus.RETRYING));
        if (!claimed) {
            log.info("Webhook delivery {} could not be claimed for dispatch (already PROCESSING, SUCCESS, or terminal)", deliveryId);
            return deliveryRepository.findById(deliveryId).orElse(null);
        }

        return executeDelivery(deliveryId);
    }

    @Transactional
    public WebhookDelivery executeDelivery(UUID deliveryId) {
        WebhookDelivery delivery = deliveryRepository.findById(deliveryId)
                .orElseThrow(() -> new IllegalArgumentException("Delivery not found: " + deliveryId));
        return executeDelivery(delivery);
    }

    @Transactional
    public WebhookDelivery executeDelivery(WebhookDelivery delivery) {
        WebhookEndpoint endpoint = delivery.getEndpoint();
        if (endpoint == null || !endpoint.isActive() || endpoint.getDeletedAt() != null) {
            log.warn("Skipping webhook delivery {}: endpoint is null, inactive, or soft-deleted", delivery.getId());
            delivery.setStatus(WebhookDeliveryStatus.FAILED);
            delivery.setLastError("Webhook endpoint is disabled, missing, or deleted");
            delivery.setClaimedAt(null);
            delivery.setNextRetryAt(null);
            return deliveryRepository.save(delivery);
        }

        String targetUrl = endpoint.getUrl();
        try {
            securityValidator.validateUrl(targetUrl);
        } catch (Exception e) {
            log.error("SSRF or invalid URL detected during webhook delivery {}: {}", delivery.getId(), e.getMessage());
            delivery.setStatus(WebhookDeliveryStatus.FAILED);
            delivery.setLastError("URL validation failed: " + e.getMessage());
            delivery.setNextRetryAt(null);
            delivery.setClaimedAt(null);
            return deliveryRepository.save(delivery);
        }

        int currentAttempt = delivery.getAttemptCount() + 1;
        delivery.setAttemptCount(currentAttempt);

        long timestamp = Instant.now().getEpochSecond();
        String payload = delivery.getPayload();
        String plainSecret = cryptoService.decrypt(endpoint.getSecret());
        String signatureHeader = signatureService.createSignatureHeader(payload, plainSecret, timestamp);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(targetUrl))
                .timeout(requestTimeout)
                .header("Content-Type", "application/json; charset=UTF-8")
                .header("User-Agent", "SwiftDrop-Webhook/1.0")
                .header("X-SwiftDrop-Event-Id", delivery.getEventId())
                .header("X-SwiftDrop-Timestamp", String.valueOf(timestamp))
                .header("X-SwiftDrop-Signature", signatureHeader)
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();

        long startTime = System.currentTimeMillis();
        Integer statusCode = null;
        String responseBody = null;
        String errorMessage = null;

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            statusCode = response.statusCode();
            responseBody = truncate(response.body(), MAX_RESPONSE_LENGTH);

            if (statusCode >= 200 && statusCode < 300) {
                delivery.setStatus(WebhookDeliveryStatus.SUCCESS);
                delivery.setDeliveredAt(Instant.now());
                delivery.setLastHttpStatus(statusCode);
                delivery.setLastError(null);
                delivery.setNextRetryAt(null);
                delivery.setClaimedAt(null);
                log.info("Webhook delivery {} succeeded on attempt {} with status {}", delivery.getId(), currentAttempt, statusCode);
            } else {
                errorMessage = "HTTP " + statusCode + ": " + (responseBody != null ? responseBody : "Error response");
                handleFailure(delivery, currentAttempt, statusCode, errorMessage);
            }
        } catch (HttpTimeoutException e) {
            errorMessage = "Request timed out after " + requestTimeout.getSeconds() + "s";
            handleFailure(delivery, currentAttempt, null, errorMessage);
        } catch (Exception e) {
            errorMessage = "Connection error: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
            handleFailure(delivery, currentAttempt, null, errorMessage);
        }

        long durationMs = System.currentTimeMillis() - startTime;

        WebhookDeliveryAttempt attempt = new WebhookDeliveryAttempt(
                delivery,
                currentAttempt,
                statusCode,
                responseBody,
                errorMessage,
                durationMs
        );
        attemptRepository.save(attempt);

        return deliveryRepository.save(delivery);
    }

    private void handleFailure(WebhookDelivery delivery, int currentAttempt, Integer statusCode, String errorMessage) {
        delivery.setLastHttpStatus(statusCode);
        delivery.setLastError(errorMessage);
        delivery.setClaimedAt(null);

        if (currentAttempt < delivery.getMaxAttempts()) {
            delivery.setStatus(WebhookDeliveryStatus.RETRYING);
            int backoffIndex = Math.min(currentAttempt - 1, BACKOFF_SECONDS.length - 1);
            long delaySeconds = BACKOFF_SECONDS[backoffIndex];
            delivery.setNextRetryAt(Instant.now().plusSeconds(delaySeconds));
            log.warn("Webhook delivery {} failed on attempt {} ({}). Retrying in {}s",
                    delivery.getId(), currentAttempt, errorMessage, delaySeconds);
        } else {
            delivery.setStatus(WebhookDeliveryStatus.FAILED);
            delivery.setNextRetryAt(null);
            log.error("Webhook delivery {} permanently failed after {} attempts: {}",
                    delivery.getId(), currentAttempt, errorMessage);
        }
    }

    private String truncate(String text, int maxLength) {
        if (text == null) {
            return null;
        }
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...[truncated]";
    }
}
