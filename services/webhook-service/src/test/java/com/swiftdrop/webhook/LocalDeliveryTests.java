package com.swiftdrop.webhook;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import com.swiftdrop.webhook.delivery.LocalTestDeliveryService;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local-webhooks")
class LocalDeliveryTests {
    private static final String SECRET = "test-only-signing-secret-at-least-32-characters";
    private static final AtomicInteger RESPONSE = new AtomicInteger(200);
    private static final HttpServer RECEIVER;
    static {
        try {
            RECEIVER = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            RECEIVER.createContext("/webhooks/swiftdrop", exchange -> {
                byte[] body = exchange.getRequestBody().readAllBytes();
                try {
                    String signature = "sha256=" + LocalTestDeliveryService.sign(new String(body, StandardCharsets.UTF_8), SECRET);
                    boolean valid = signature.equals(exchange.getRequestHeaders().getFirst("X-SwiftDrop-Signature"));
                    exchange.sendResponseHeaders(valid ? RESPONSE.get() : 401, -1);
                } catch (Exception ex) { exchange.sendResponseHeaders(500, -1); }
                finally { exchange.close(); }
            });
            RECEIVER.start();
        } catch (Exception ex) { throw new ExceptionInInitializerError(ex); }
    }
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("webhooks.local.receiver-url", () -> url());
        registry.add("webhooks.local.signing-secret", () -> SECRET);
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:delivery-test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
    }
    static String url() { return "http://127.0.0.1:" + RECEIVER.getAddress().getPort() + "/webhooks/swiftdrop"; }
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired com.swiftdrop.webhook.delivery.DeliveryWorkflow workflow;
    @BeforeEach void clean() {
        jdbc.update("DELETE FROM webhooks_dev.dead_letter_outbox"); jdbc.update("DELETE FROM webhooks_dev.delivery_attempts"); jdbc.update("DELETE FROM webhooks_dev.deliveries"); jdbc.update("DELETE FROM webhooks_dev.test_deliveries");
        jdbc.update("DELETE FROM webhooks_dev.subscriptions");
        RESPONSE.set(200);
    }
    @AfterAll static void stop() { RECEIVER.stop(0); }
    UUID subscription(String owner, String callback, boolean active) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO webhooks_dev.subscriptions(id,merchant_id,callback_url,event_type,is_active) VALUES(?,?,?,?,?)",
                id, owner, callback, "parcel.status.changed", active);
        return id;
    }
    @Test void signedDeliverySucceedsAndIsRecorded() throws Exception {
        UUID id = subscription("dev-nathasha", url(), true);
        mvc.perform(post("/api/v1/merchant/webhooks/" + id + "/test").header("X-SwiftDrop-Local", "true"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.responseCode").value(200));
        assertEquals("SUCCESS", jdbc.queryForObject("SELECT status FROM webhooks_dev.test_deliveries", String.class));
    }
    @Test void failureIsRecordedWithoutFollowingRedirects() throws Exception {
        RESPONSE.set(302);
        UUID id = subscription("dev-nathasha", url(), true);
        mvc.perform(post("/api/v1/merchant/webhooks/" + id + "/test").header("X-SwiftDrop-Local", "true"))
                .andExpect(jsonPath("$.status").value("FAILED")).andExpect(jsonPath("$.responseCode").value(302));
        assertEquals("FAILED", jdbc.queryForObject("SELECT status FROM webhooks_dev.test_deliveries", String.class));
    }
    @Test void rejectsDisabledForeignAndArbitraryDestinations() throws Exception {
        UUID disabled = subscription("dev-nathasha", url(), false);
        UUID foreign = subscription("other-merchant", url(), true);
        UUID external = subscription("dev-nathasha", "https://example.com", true);
        mvc.perform(post("/api/v1/merchant/webhooks/" + disabled + "/test").header("X-SwiftDrop-Local", "true"))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/v1/merchant/webhooks/" + foreign + "/test").header("X-SwiftDrop-Local", "true"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/merchant/webhooks/" + external + "/test").header("X-SwiftDrop-Local", "true"))
                .andExpect(status().isBadRequest());
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM webhooks_dev.test_deliveries", Integer.class));
    }
    @Test void hmacMatchesKnownVector() throws Exception {
        assertEquals("f7bc83f430538424b13298e6aa6fb143ef4d59a14946175997479dbc2d1a3cd8",
                LocalTestDeliveryService.sign("The quick brown fox jumps over the lazy dog", "key"));
    }

    @Test void logFiltersPaginatesAndProtectsDetails() throws Exception {
        UUID own = subscription("dev-nathasha", url(), true);
        UUID other = subscription("someone-else", url(), true);
        UUID visible = UUID.randomUUID();
        UUID hidden = UUID.randomUUID();
        jdbc.update("INSERT INTO webhooks_dev.deliveries(id,subscription_id,event_id,callback_url,payload,status) VALUES(?,?,RANDOM_UUID(),'http://127.0.0.1:9091/webhooks/swiftdrop',?,?)", visible, own, "{\"trackingNumber\":\"SD-TEST\"}", "SUCCESS");
        jdbc.update("INSERT INTO webhooks_dev.deliveries(id,subscription_id,event_id,callback_url,payload,status) VALUES(?,?,RANDOM_UUID(),'http://127.0.0.1:9091/webhooks/swiftdrop',?,?)", UUID.randomUUID(), own, "{}", "DEAD_LETTER");
        jdbc.update("INSERT INTO webhooks_dev.deliveries(id,subscription_id,event_id,callback_url,payload,status) VALUES(?,?,RANDOM_UUID(),'http://127.0.0.1:9091/webhooks/swiftdrop',?,?)", hidden, other, "{\"trackingNumber\":\"SD-TEST\"}", "SUCCESS");
        String path = "/api/v1/merchant/webhooks/deliveries";
        mvc.perform(get(path).header("X-SwiftDrop-Local", "true").param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(2)).andExpect(jsonPath("$.items.length()").value(1));
        mvc.perform(get(path).header("X-SwiftDrop-Local", "true").param("status", "SUCCESS").param("search", "sd-test"))
                .andExpect(jsonPath("$.total").value(1)).andExpect(jsonPath("$.items[0].id").value(visible.toString()));
        mvc.perform(get(path + "/" + visible).header("X-SwiftDrop-Local", "true"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.payload").value("{\"trackingNumber\":\"SD-TEST\"}"));
        mvc.perform(get(path + "/" + hidden).header("X-SwiftDrop-Local", "true")).andExpect(status().isNotFound());
        mvc.perform(get(path).header("X-SwiftDrop-Local", "true").param("page", "-1")).andExpect(status().isBadRequest());
        mvc.perform(get(path).header("X-SwiftDrop-Local", "true").param("size", "101")).andExpect(status().isBadRequest());
    }

    @Test void retriesThreeTimesThenDeadLettersAndReplaysWithHistory() throws Exception {
        UUID sub=subscription("dev-nathasha",url(),true);
        RESPONSE.set(500);
        UUID id=workflow.sample(sub).id();
        for(int attempt=1;attempt<=3;attempt++) {
            jdbc.update("UPDATE webhooks_dev.deliveries SET next_attempt_at=CURRENT_TIMESTAMP WHERE id=?",id);
            workflow.tick();
            assertEquals(attempt,jdbc.queryForObject("SELECT attempts FROM webhooks_dev.deliveries WHERE id=?",Integer.class,id));
            assertEquals(attempt==3?"DEAD_LETTER":"RETRYING",jdbc.queryForObject("SELECT status FROM webhooks_dev.deliveries WHERE id=?",String.class,id));
        }
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM webhooks_dev.dead_letter_outbox",Integer.class));
        String payload=jdbc.queryForObject("SELECT payload FROM webhooks_dev.deliveries WHERE id=?",String.class,id);
        workflow.replay(id);
        assertThrows(org.springframework.web.server.ResponseStatusException.class,()->workflow.replay(id));
        RESPONSE.set(200); workflow.tick();
        assertEquals("SUCCESS",jdbc.queryForObject("SELECT status FROM webhooks_dev.deliveries WHERE id=?",String.class,id));
        assertEquals(payload,jdbc.queryForObject("SELECT payload FROM webhooks_dev.deliveries WHERE id=?",String.class,id));
        assertEquals(4,jdbc.queryForObject("SELECT count(*) FROM webhooks_dev.delivery_attempts WHERE delivery_id=?",Integer.class,id));
    }

    @Test void brokerEventsAreDeduplicatedAndRespectMerchantScope() {
        subscription("dev-nathasha",url(),true);
        String event="{\"eventId\":\"event-123\",\"merchantId\":\"dev-nathasha\",\"event\":\"parcel.status.changed\",\"trackingNumber\":\"SD1\",\"status\":\"DELIVERED\",\"test\":true}";
        workflow.ingest(event);workflow.ingest(event);workflow.ingest(event.replace("dev-nathasha","other-merchant"));
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM webhooks_dev.deliveries",Integer.class));
        assertThrows(IllegalArgumentException.class,()->workflow.ingest("{}"));
        workflow.tick();workflow.tick();
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM webhooks_dev.delivery_attempts",Integer.class));
    }

    @Test void rejectsMalformedEventsAndRetainsExactPayloadAndDestination() {
        for(String invalid: new String[]{"", "null", "[]", "{} {}", "{\"eventId\":123}", "{"}) {
            assertThrows(IllegalArgumentException.class, () -> workflow.ingest(invalid));
        }
        UUID sub=subscription("dev-nathasha",url(),true);
        String payload="{ \"eventId\": \"utf8-event\", \"merchantId\": \"dev-nathasha\", \"event\": \"parcel.status.changed\", \"trackingNumber\": \"SD-සිංහල\", \"status\": \"DELIVERED\", \"test\": true }";
        workflow.ingest(payload);
        jdbc.update("UPDATE webhooks_dev.subscriptions SET callback_url='https://example.com/changed' WHERE id=?",sub);
        workflow.tick();
        assertEquals(payload,jdbc.queryForObject("SELECT payload FROM webhooks_dev.deliveries",String.class));
        assertEquals(url(),jdbc.queryForObject("SELECT callback_url FROM webhooks_dev.deliveries",String.class));
        assertEquals("SUCCESS",jdbc.queryForObject("SELECT status FROM webhooks_dev.deliveries",String.class));
    }

    @Test void expiredLeasesRecoverAndDisabledSubscriptionsCannotReplay() {
        UUID sub=subscription("dev-nathasha",url(),true);
        UUID id=workflow.sample(sub).id();
        jdbc.update("UPDATE webhooks_dev.deliveries SET status='PROCESSING',lease_until=? WHERE id=?",java.time.OffsetDateTime.now().minusMinutes(5),id);
        workflow.tick();
        assertEquals("SUCCESS",jdbc.queryForObject("SELECT status FROM webhooks_dev.deliveries WHERE id=?",String.class,id));
        assertThrows(org.springframework.web.server.ResponseStatusException.class,()->workflow.replay(id));
        jdbc.update("UPDATE webhooks_dev.deliveries SET status='DEAD_LETTER' WHERE id=?",id);
        jdbc.update("UPDATE webhooks_dev.subscriptions SET is_active=false WHERE id=?",sub);
        assertThrows(org.springframework.web.server.ResponseStatusException.class,()->workflow.replay(id));
    }

    @Test void outboxWaitsForConfirmationAndRejectsMalformedBrokerMessages() {
        UUID sub=subscription("dev-nathasha",url(),true);
        UUID id=workflow.sample(sub).id();
        UUID outbox=UUID.randomUUID();
        jdbc.update("INSERT INTO webhooks_dev.dead_letter_outbox(id,delivery_id) VALUES(?,?)",outbox,id);
        class ConfirmingTemplate extends org.springframework.amqp.rabbit.core.RabbitTemplate {
            boolean ack;
            @Override public void send(String exchange, String routingKey, org.springframework.amqp.core.Message message,
                    org.springframework.amqp.rabbit.connection.CorrelationData data) {
                data.getFuture().complete(new org.springframework.amqp.rabbit.connection.CorrelationData.Confirm(ack, ack ? null : "nack"));
            }
        }
        var rabbit=new ConfirmingTemplate();
        var broker=new com.swiftdrop.webhook.delivery.WebhookBroker(rabbit,jdbc,workflow);
        org.springframework.test.util.ReflectionTestUtils.setField(broker,"deadExchange","test.dead");
        broker.publishDeadLetters();
        assertEquals(false,jdbc.queryForObject("SELECT published FROM webhooks_dev.dead_letter_outbox WHERE id=?",Boolean.class,outbox));
        rabbit.ack=true;
        broker.publishDeadLetters();
        assertEquals(true,jdbc.queryForObject("SELECT published FROM webhooks_dev.dead_letter_outbox WHERE id=?",Boolean.class,outbox));
        assertThrows(org.springframework.amqp.AmqpRejectAndDontRequeueException.class,()->broker.consume(new org.springframework.amqp.core.Message(new byte[0])));
    }
}
