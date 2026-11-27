package com.swiftdrop.merchant.service.webhook;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.swiftdrop.merchant.entity.Merchant;
import com.swiftdrop.merchant.entity.WebhookDelivery;
import com.swiftdrop.merchant.entity.WebhookDeliveryAttempt;
import com.swiftdrop.merchant.entity.WebhookEndpoint;
import com.swiftdrop.merchant.entity.enums.WebhookDeliveryStatus;
import com.swiftdrop.merchant.repository.WebhookDeliveryAttemptRepository;
import com.swiftdrop.merchant.repository.WebhookDeliveryRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WebhookDispatcherServiceTest {

    @Mock
    private WebhookDeliveryRepository deliveryRepository;

    @Mock
    private WebhookDeliveryAttemptRepository attemptRepository;

    private WebhookSignatureService signatureService;
    private WebhookSecretCryptoService cryptoService;
    private WebhookSecurityValidator devSecurityValidator;
    private WebhookDispatcherService dispatcherService;

    private HttpServer server;
    private int port;
    private final AtomicInteger requestedStatusCode = new AtomicInteger(200);
    private final AtomicReference<String> receivedPayload = new AtomicReference<>();
    private final AtomicReference<String> receivedSignature = new AtomicReference<>();
    private final AtomicReference<String> receivedTimestamp = new AtomicReference<>();
    private final AtomicReference<String> receivedEventId = new AtomicReference<>();

    @BeforeEach
    void setUp() throws IOException {
        signatureService = new WebhookSignatureService();
        cryptoService = new WebhookSecretCryptoService("dGVzdC1lbmNyeXB0aW9uLWtleS0zMi1ieXRlcy1sb25nISE=");
        devSecurityValidator = new WebhookSecurityValidator(true, true);
        dispatcherService = new WebhookDispatcherService(
                deliveryRepository,
                attemptRepository,
                signatureService,
                cryptoService,
                devSecurityValidator,
                3
        );

        org.mockito.Mockito.lenient().when(deliveryRepository.save(any(WebhookDelivery.class))).thenAnswer(i -> i.getArgument(0));

        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/webhook", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                byte[] bytes = exchange.getRequestBody().readAllBytes();
                receivedPayload.set(new String(bytes, StandardCharsets.UTF_8));
                receivedSignature.set(exchange.getRequestHeaders().getFirst("X-SwiftDrop-Signature"));
                receivedTimestamp.set(exchange.getRequestHeaders().getFirst("X-SwiftDrop-Timestamp"));
                receivedEventId.set(exchange.getRequestHeaders().getFirst("X-SwiftDrop-Event-Id"));

                int status = requestedStatusCode.get();
                String response = status >= 200 && status < 300 ? "{\"status\":\"ok\"}" : "Internal Error";
                byte[] respBytes = response.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(status, respBytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(respBytes);
                }
            }
        });
        server.start();
        port = server.getAddress().getPort();
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void shouldSuccessfullyDispatchWebhookWithEncryptedSecret() {
        requestedStatusCode.set(200);

        String rawSecret = "whsec_supersecretkey123456789";
        String encryptedSecret = cryptoService.encrypt(rawSecret);

        Merchant merchant = new Merchant();
        merchant.setId(UUID.randomUUID());

        WebhookEndpoint endpoint = new WebhookEndpoint(
                merchant,
                "http://127.0.0.1:" + port + "/webhook",
                encryptedSecret,
                "Local test hook",
                List.of("PARCEL_CREATED")
        );
        endpoint.setId(UUID.randomUUID());

        String jsonPayload = "{\"trackingNumber\":\"SWF-999\",\"eventType\":\"PARCEL_CREATED\"}";
        WebhookDelivery delivery = new WebhookDelivery(
                endpoint,
                merchant.getId(),
                "evt_test_123",
                "PARCEL_CREATED",
                "SWF-999",
                "pcl-999",
                jsonPayload
        );
        delivery.setId(UUID.randomUUID());

        WebhookDelivery result = dispatcherService.executeDelivery(delivery);

        assertEquals(WebhookDeliveryStatus.SUCCESS, result.getStatus());
        assertEquals(1, result.getAttemptCount());
        assertEquals(200, result.getLastHttpStatus());
        assertNotNull(result.getDeliveredAt());
        assertNull(result.getLastError());
        assertNull(result.getNextRetryAt());

        // Verify HTTP headers and signature received by mock server
        assertEquals(jsonPayload, receivedPayload.get());
        assertEquals("evt_test_123", receivedEventId.get());
        assertNotNull(receivedSignature.get());

        // Verify HMAC-SHA256 signature using the raw decrypted secret
        boolean signatureValid = signatureService.verifySignature(
                jsonPayload,
                receivedSignature.get(),
                rawSecret
        );
        assertTrue(signatureValid, "Signature sent to webhook endpoint must verify using the raw secret");

        // Verify attempt was recorded
        ArgumentCaptor<WebhookDeliveryAttempt> attemptCaptor = ArgumentCaptor.forClass(WebhookDeliveryAttempt.class);
        verify(attemptRepository).save(attemptCaptor.capture());
        WebhookDeliveryAttempt attempt = attemptCaptor.getValue();
        assertEquals(1, attempt.getAttemptNumber());
        assertEquals(200, attempt.getHttpStatus());
        assertNotNull(attempt.getResponseBody());
        assertTrue(attempt.getDurationMs() >= 0);
    }

    @Test
    void shouldPreventDoubleDispatchWhenAtomicClaimingFails() {
        UUID deliveryId = UUID.randomUUID();
        when(deliveryRepository.claimDelivery(eq(deliveryId), eq(WebhookDeliveryStatus.PROCESSING), any(Instant.class), any()))
                .thenReturn(0); // 0 rows updated means already claimed or completed

        WebhookDelivery existing = new WebhookDelivery();
        existing.setId(deliveryId);
        existing.setStatus(WebhookDeliveryStatus.PROCESSING);
        when(deliveryRepository.findById(deliveryId)).thenReturn(Optional.of(existing));

        WebhookDelivery result = dispatcherService.dispatch(deliveryId);

        assertNotNull(result);
        assertEquals(WebhookDeliveryStatus.PROCESSING, result.getStatus());
        // Verify attempt was never saved because delivery was skipped
        verify(attemptRepository, never()).save(any());
    }

    @Test
    void shouldRejectDeliveryWhenEndpointIsSoftDeleted() {
        Merchant merchant = new Merchant();
        merchant.setId(UUID.randomUUID());

        WebhookEndpoint endpoint = new WebhookEndpoint(
                merchant,
                "http://127.0.0.1:" + port + "/webhook",
                "whsec_test",
                "Deleted hook",
                List.of("PARCEL_CREATED")
        );
        endpoint.setId(UUID.randomUUID());
        endpoint.setDeletedAt(Instant.now()); // Soft-deleted

        WebhookDelivery delivery = new WebhookDelivery(
                endpoint,
                merchant.getId(),
                "evt_deleted",
                "PARCEL_CREATED",
                "TRK001",
                "pcl-001",
                "{}"
        );
        delivery.setId(UUID.randomUUID());

        WebhookDelivery result = dispatcherService.executeDelivery(delivery);

        assertEquals(WebhookDeliveryStatus.FAILED, result.getStatus());
        assertTrue(result.getLastError().contains("deleted"));
    }

    @Test
    void shouldHandleHttpFailureAndScheduleRetry() {
        requestedStatusCode.set(500);

        String secret = "whsec_test";
        Merchant merchant = new Merchant();
        merchant.setId(UUID.randomUUID());

        WebhookEndpoint endpoint = new WebhookEndpoint(
                merchant,
                "http://127.0.0.1:" + port + "/webhook",
                secret,
                "Local test hook",
                List.of("PARCEL_CREATED")
        );
        endpoint.setId(UUID.randomUUID());

        WebhookDelivery delivery = new WebhookDelivery(
                endpoint,
                merchant.getId(),
                "evt_fail",
                "PARCEL_CREATED",
                "TRK002",
                "pcl-002",
                "{}"
        );
        delivery.setId(UUID.randomUUID());

        WebhookDelivery result = dispatcherService.executeDelivery(delivery);

        assertEquals(WebhookDeliveryStatus.RETRYING, result.getStatus());
        assertEquals(1, result.getAttemptCount());
        assertNotNull(result.getNextRetryAt());
        assertEquals(500, result.getLastHttpStatus());
        assertTrue(result.getLastError().contains("500"));
    }

    @Test
    void shouldMarkFailedWhenMaxAttemptsReached() {
        requestedStatusCode.set(500);

        String secret = "whsec_test";
        Merchant merchant = new Merchant();
        merchant.setId(UUID.randomUUID());

        WebhookEndpoint endpoint = new WebhookEndpoint(
                merchant,
                "http://127.0.0.1:" + port + "/webhook",
                secret,
                "Local test hook",
                List.of("PARCEL_CREATED")
        );
        endpoint.setId(UUID.randomUUID());

        WebhookDelivery delivery = new WebhookDelivery(
                endpoint,
                merchant.getId(),
                "evt_fail_max",
                "PARCEL_CREATED",
                "TRK003",
                "pcl-003",
                "{}"
        );
        delivery.setId(UUID.randomUUID());
        delivery.setAttemptCount(4);
        delivery.setMaxAttempts(5);

        WebhookDelivery result = dispatcherService.executeDelivery(delivery);

        assertEquals(WebhookDeliveryStatus.FAILED, result.getStatus());
        assertEquals(5, result.getAttemptCount());
        assertNull(result.getNextRetryAt());
    }
}
