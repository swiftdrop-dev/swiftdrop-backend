package com.swiftdrop.webhook.delivery;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.webhook.subscription.LocalWebhookController;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** One bounded, synchronous LOCAL test. Production delivery will consume broker events. */
@Service
@Profile("local-webhooks")
public class LocalTestDeliveryService {
    private final JdbcTemplate jdbc;
    private final LocalWebhookController subscriptions;
    private final ObjectMapper json;
    private final String secret;
    private final URI receiver;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2))
            .followRedirects(HttpClient.Redirect.NEVER).build();

    public LocalTestDeliveryService(JdbcTemplate jdbc, LocalWebhookController subscriptions, ObjectMapper json,
            @Value("${webhooks.local.signing-secret:}") String secret,
            @Value("${webhooks.local.receiver-url}") String receiverUrl) {
        this.jdbc = jdbc; this.subscriptions = subscriptions; this.json = json; this.secret = secret;
        this.receiver = URI.create(receiverUrl);
        if (!"http".equals(receiver.getScheme()) || !"127.0.0.1".equals(receiver.getHost())
                || receiver.getPort() < 1024 || !"/webhooks/swiftdrop".equals(receiver.getPath())
                || receiver.getRawQuery() != null || receiver.getUserInfo() != null || receiver.getFragment() != null) {
            throw new IllegalStateException("Mock receiver must be an explicit loopback endpoint");
        }
    }

    public record Result(UUID id, String status, Integer responseCode, String failureReason) {}

    public Result send(UUID subscriptionId) {
        var subscription = subscriptions.get(subscriptionId);
        if (!subscription.isActive()) throw new ResponseStatusException(HttpStatus.CONFLICT);
        // No arbitrary outbound requests, even for already-stored HTTPS subscriptions.
        if (!receiver.toString().equals(subscription.callbackUrl())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        if (secret.length() < 32) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE);
        UUID id = UUID.randomUUID();
        final String payload;
        final String signature;
        try {
            payload = json.writeValueAsString(Map.of("eventId", id.toString(), "event", "parcel.status.changed",
                    "trackingNumber", "SD-LOCAL-TEST", "merchantOrderRef", "LOCAL-TEST-ORDER",
                    "status", "DELIVERED", "timestamp", Instant.now().toString(), "test", true));
            signature = sign(payload, secret);
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR);
        }
        jdbc.update("INSERT INTO webhooks_dev.test_deliveries(id,subscription_id,payload,status) VALUES(?,?,?,?)",
                id, subscriptionId, payload, "PENDING");
        Integer code = null;
        String failure = null;
        String status = "FAILED";
        try {
            var request = HttpRequest.newBuilder(receiver).timeout(Duration.ofSeconds(5))
                    .header("Content-Type", "application/json")
                    .header("X-SwiftDrop-Signature", "sha256=" + signature)
                    .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8)).build();
            code = client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
            if (code >= 200 && code < 300) status = "SUCCESS";
            else failure = "Receiver returned a non-success status";
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt(); failure = "Delivery interrupted";
        } catch (java.io.IOException ex) {
            failure = "Receiver unavailable or timed out";
        }
        jdbc.update("UPDATE webhooks_dev.test_deliveries SET status=?, response_code=?, failure_reason=?, completed_at=CURRENT_TIMESTAMP WHERE id=?",
                status, code, failure, id);
        return new Result(id, status, code, failure);
    }

    public static String sign(String payload, String secret) throws java.security.GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
    }
}
