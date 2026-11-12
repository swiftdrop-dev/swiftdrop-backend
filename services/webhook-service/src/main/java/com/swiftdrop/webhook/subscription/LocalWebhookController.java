package com.swiftdrop.webhook.subscription;

import java.net.URI;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import com.swiftdrop.webhook.security.MerchantIdentity;

/** Development-only API. The owner is server-configured, never taken from the browser. */
@RestController
@Profile("local-webhooks")
@RequestMapping("/api/v1/merchant/webhooks")
public class LocalWebhookController {
    private final JdbcTemplate jdbc;
    private final MerchantIdentity identity;
    private final String receiverUrl;

    public LocalWebhookController(JdbcTemplate jdbc,
            MerchantIdentity identity,
            @Value("${server.address:}") String address,
            @Value("${webhooks.local.receiver-url}") String receiverUrl) {
        if (!"127.0.0.1".equals(address)) {
            throw new IllegalStateException("Local webhook mode must bind to 127.0.0.1");
        }
        this.jdbc = jdbc;
        this.identity = identity;
        this.receiverUrl = receiverUrl;
    }

    public record Subscription(UUID id, String callbackUrl, List<String> eventTypes,
            boolean isActive, OffsetDateTime createdAt) {}
    public record Input(String callbackUrl, List<String> eventTypes, Boolean isActive) {}

    private Subscription map(java.sql.ResultSet row, int index) throws java.sql.SQLException {
        return new Subscription(row.getObject("id", UUID.class), row.getString("callback_url"),
                List.of(row.getString("event_type")), row.getBoolean("is_active"),
                row.getObject("created_at", OffsetDateTime.class));
    }

    @GetMapping
    public List<Subscription> list() {
        String merchantId = identity.currentMerchantId();
        return jdbc.query("SELECT * FROM webhooks_dev.subscriptions WHERE merchant_id = ? ORDER BY created_at, id",
                this::map, merchantId);
    }

    @GetMapping("/{id}")
    public Subscription get(@PathVariable UUID id) {
        String merchantId = identity.currentMerchantId();
        return jdbc.query("SELECT * FROM webhooks_dev.subscriptions WHERE id = ? AND merchant_id = ?",
                this::map, id, merchantId).stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Subscription create(@RequestBody Input input) {
        String merchantId = identity.currentMerchantId();
        validate(input);
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO webhooks_dev.subscriptions (id, merchant_id, callback_url, event_type) VALUES (?, ?, ?, ?)",
                id, merchantId, input.callbackUrl().trim(), "parcel.status.changed");
        return get(id);
    }

    @PatchMapping("/{id}")
    public Subscription update(@PathVariable UUID id, @RequestBody Input input) {
        String merchantId = identity.currentMerchantId();
        get(id);
        int updated;
        if (input.callbackUrl() == null && input.eventTypes() == null && input.isActive() != null) {
            updated = jdbc.update("UPDATE webhooks_dev.subscriptions SET is_active = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ? AND merchant_id = ?",
                    input.isActive(), id, merchantId);
        } else {
            validate(input);
            updated = jdbc.update("UPDATE webhooks_dev.subscriptions SET callback_url = ?, event_type = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ? AND merchant_id = ?",
                    input.callbackUrl().trim(), "parcel.status.changed", id, merchantId);
        }
        if (updated != 1) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return get(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        get(id);
        try {
            int removed = jdbc.update("DELETE FROM webhooks_dev.subscriptions WHERE id=? AND merchant_id=?",
                    id, identity.currentMerchantId());
            if (removed != 1) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        } catch (org.springframework.dao.DataIntegrityViolationException ex) {
            // Foreign keys retain delivery and replay history; disable used subscriptions instead.
            throw new ResponseStatusException(HttpStatus.CONFLICT);
        }
    }

    private void validate(Input input) {
        try {
            if (input.callbackUrl() == null || input.callbackUrl().length() > 2048
                    || !List.of("parcel.status.changed").equals(input.eventTypes())) throw new IllegalArgumentException();
            URI url = URI.create(input.callbackUrl().trim());
            if ((!"https".equalsIgnoreCase(url.getScheme()) && !receiverUrl.equals(input.callbackUrl().trim())) || url.getHost() == null
                    || url.getUserInfo() != null || url.getFragment() != null) throw new IllegalArgumentException();
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
    }
}
