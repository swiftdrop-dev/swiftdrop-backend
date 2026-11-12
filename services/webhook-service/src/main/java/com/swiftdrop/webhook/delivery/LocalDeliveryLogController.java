package com.swiftdrop.webhook.delivery;

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

@RestController
@Profile("local-webhooks")
@RequestMapping("/api/v1/merchant/webhooks/deliveries")
public class LocalDeliveryLogController {
    private final JdbcTemplate jdbc;
    private final MerchantIdentity identity;
    private static final String JOIN = " FROM webhooks_dev.deliveries d JOIN webhooks_dev.subscriptions s ON s.id=d.subscription_id ";
    public LocalDeliveryLogController(JdbcTemplate jdbc, MerchantIdentity identity) {
        this.jdbc = jdbc; this.identity = identity;
    }
    public record Delivery(UUID id, UUID subscriptionId, String status, Integer responseCode,
            String failureReason, OffsetDateTime createdAt, OffsetDateTime completedAt,
            int attempts,int replayCount,String callbackUrl,OffsetDateTime nextAttemptAt,String eventId) {}
    public record Page(List<Delivery> items, long total, int page, int size) {}
    public record Detail(Delivery delivery, String payload, List<java.util.Map<String,Object>> attempts) {}
    private Delivery map(java.sql.ResultSet row, int index) throws java.sql.SQLException {
        return new Delivery(row.getObject("id", UUID.class), row.getObject("subscription_id", UUID.class),
                row.getString("status"), row.getObject("response_code", Integer.class), row.getString("failure_reason"),
                row.getObject("created_at", OffsetDateTime.class), row.getObject("completed_at", OffsetDateTime.class),
                row.getInt("attempts"),row.getInt("replay_count"),row.getString("callback_url"),row.getObject("next_attempt_at",OffsetDateTime.class),row.getString("event_id"));
    }
    @GetMapping
    public Page list(@RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="10") int size,
            @RequestParam(defaultValue="") String status, @RequestParam(defaultValue="") String search) {
        if (page < 0 || page > 100000 || size < 1 || size > 100 || search.length() > 200
                || (!status.isEmpty() && !List.of("PENDING", "PROCESSING", "RETRYING", "SUCCESS", "DEAD_LETTER").contains(status))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
        String where = " WHERE s.merchant_id=? AND (?='' OR d.status=?) "
                + "AND (POSITION(? IN LOWER(d.payload))>0 OR POSITION(? IN CAST(d.id AS VARCHAR))>0)";
        String query = search.trim().toLowerCase(java.util.Locale.ROOT);
        String merchant = identity.currentMerchantId();
        Long total = jdbc.queryForObject("SELECT COUNT(*)" + JOIN + where, Long.class, merchant, status, status, query, query);
        List<Delivery> rows = jdbc.query("SELECT d.*" + JOIN + where + " ORDER BY d.created_at DESC, d.id DESC LIMIT ? OFFSET ?",
                this::map, merchant, status, status, query, query, size, (long)page * size);
        return new Page(rows, total == null ? 0 : total, page, size);
    }
    @GetMapping("/{id}")
    public Detail detail(@PathVariable UUID id) {
        String merchant = identity.currentMerchantId();
        var result=jdbc.query("SELECT d.*" + JOIN + " WHERE d.id=? AND s.merchant_id=?",
                (row, index) -> new Detail(map(row, index), row.getString("payload"),List.of()), id, merchant)
                .stream().findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return new Detail(result.delivery(),result.payload(),jdbc.queryForList("SELECT attempt_number,replay_number,response_code,failure_reason,created_at FROM webhooks_dev.delivery_attempts WHERE delivery_id=? ORDER BY created_at,id",id));
    }
}
