package com.swiftdrop.webhook;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class WebhookServiceApplicationTests {
    @Autowired MockMvc mvc;

    @Test
    void contextLoads() {
    }

    @Test
    void localApiIsDisabledByDefault() throws Exception {
        mvc.perform(get("/api/v1/merchant/webhooks")).andExpect(status().isNotFound());
    }
}
