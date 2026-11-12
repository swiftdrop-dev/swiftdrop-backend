package com.swiftdrop.webhook;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local-webhooks")
class LocalWebhookTests {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    private static final String PATH = "/api/v1/merchant/webhooks";
    private static final String BODY = """
        {"callbackUrl":"https://shop.example.com/callback","eventTypes":["parcel.status.changed"]}
        """;

    @BeforeEach void clean() {
        jdbc.update("DELETE FROM webhooks_dev.dead_letter_outbox"); jdbc.update("DELETE FROM webhooks_dev.delivery_attempts"); jdbc.update("DELETE FROM webhooks_dev.deliveries"); jdbc.update("DELETE FROM webhooks_dev.test_deliveries");
        jdbc.update("DELETE FROM webhooks_dev.subscriptions");
    }

    @Test void createsPersistsUpdatesAndDisables() throws Exception {
        mvc.perform(post(PATH).header("X-SwiftDrop-Local", "true").contentType("application/json").content(BODY))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.isActive").value(true));
        UUID id = jdbc.queryForObject("SELECT id FROM webhooks_dev.subscriptions", UUID.class);
        mvc.perform(get(PATH).header("X-SwiftDrop-Local", "true"))
                .andExpect(jsonPath("$[0].id").value(id.toString()));
        mvc.perform(patch(PATH + "/" + id).header("X-SwiftDrop-Local", "true")
                .contentType("application/json").content(BODY.replace("/callback", "/updated")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.callbackUrl").value("https://shop.example.com/updated"));
        mvc.perform(patch(PATH + "/" + id).header("X-SwiftDrop-Local", "true")
                .contentType("application/json").content("{\"isActive\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.isActive").value(false));
    }

    @Test void isolatesOtherMerchantRecords() throws Exception {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO webhooks_dev.subscriptions(id,merchant_id,callback_url,event_type) VALUES(?,?,?,?)",
                id, "another-merchant", "https://example.com", "parcel.status.changed");
        mvc.perform(get(PATH).header("X-SwiftDrop-Local", "true")).andExpect(content().json("[]"));
        mvc.perform(patch(PATH + "/" + id).header("X-SwiftDrop-Local", "true")
                .contentType("application/json").content("{\"isActive\":false}"))
                .andExpect(status().isNotFound());
    }

    @Test void rejectsInvalidInputAndUntrustedOrigins() throws Exception {
        mvc.perform(post(PATH).header("X-SwiftDrop-Local", "true").contentType("application/json")
                .content(BODY.replace("https:", "http:"))).andExpect(status().isBadRequest());
        mvc.perform(post(PATH).header("X-SwiftDrop-Local", "true").contentType("application/json")
                .content(BODY.replace("parcel.status.changed", "unknown"))).andExpect(status().isBadRequest());
        mvc.perform(get(PATH)).andExpect(status().isForbidden());
        mvc.perform(get(PATH).header("X-SwiftDrop-Local", "true").header("Origin", "https://untrusted.example"))
                .andExpect(status().isForbidden());
        mvc.perform(options(PATH).header("Origin", "http://localhost:5174"))
                .andExpect(status().isNoContent()).andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5174"));
    }

    @Test void deletesUnusedSubscriptionsButPreservesDeliveryHistory() throws Exception {
        mvc.perform(post(PATH).header("X-SwiftDrop-Local", "true").contentType("application/json").content(BODY))
                .andExpect(status().isCreated());
        UUID id = jdbc.queryForObject("SELECT id FROM webhooks_dev.subscriptions", UUID.class);
        mvc.perform(delete(PATH + "/" + id).header("X-SwiftDrop-Local", "true")).andExpect(status().isNoContent());
        mvc.perform(get(PATH + "/" + id).header("X-SwiftDrop-Local", "true")).andExpect(status().isNotFound());
        mvc.perform(post(PATH).header("X-SwiftDrop-Local", "true").contentType("application/json").content(BODY));
        UUID used = jdbc.queryForObject("SELECT id FROM webhooks_dev.subscriptions", UUID.class);
        jdbc.update("INSERT INTO webhooks_dev.test_deliveries(id,subscription_id,payload,status) VALUES(?,?,?,?)",UUID.randomUUID(),used,"{}","FAILED");
        mvc.perform(delete(PATH + "/" + used).header("X-SwiftDrop-Local", "true")).andExpect(status().isConflict());
        mvc.perform(get(PATH + "/" + used).header("X-SwiftDrop-Local", "true")).andExpect(status().isOk());
    }
}
