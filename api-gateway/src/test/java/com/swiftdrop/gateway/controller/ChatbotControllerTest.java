package com.swiftdrop.gateway.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.gateway.dto.request.ChatbotMessageRequest;
import com.swiftdrop.gateway.service.ChatbotService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ChatbotControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ChatbotService chatbotService;

    private final String testUser = "test-user-unit";

    @BeforeEach
    void setUp() {
        chatbotService.resetUsage(testUser);
    }

    @Test
    void shouldReturnGreetingWhenAsked() throws Exception {
        ChatbotMessageRequest request = new ChatbotMessageRequest("Hello SwiftBot", testUser);

        mockMvc.perform(post("/api/v1/chatbot/message")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply", containsString("SwiftBot")))
                .andExpect(jsonPath("$.remainingUses", is(3)))
                .andExpect(jsonPath("$.totalLimit", is(4)));
    }

    @Test
    void shouldEnforce4MessageLimit() throws Exception {
        ChatbotMessageRequest request = new ChatbotMessageRequest("How do I track my parcel?", testUser);

        // Perform 4 successful requests
        for (int i = 0; i < 4; i++) {
            mockMvc.perform(post("/api/v1/chatbot/message")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk());
        }

        // The 5th request should fail with HTTP 429 Too Many Requests
        mockMvc.perform(post("/api/v1/chatbot/message")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().is4xxClientError())
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.status", is(429)))
                .andExpect(jsonPath("$.message", containsString("limit")));
    }

    @Test
    void shouldReturnUsageStats() throws Exception {
        mockMvc.perform(get("/api/v1/chatbot/usage")
                        .param("userId", testUser))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId", is(testUser)))
                .andExpect(jsonPath("$.totalLimit", is(4)))
                .andExpect(jsonPath("$.usedCount", is(0)))
                .andExpect(jsonPath("$.remainingUses", is(4)))
                .andExpect(jsonPath("$.limitReached", is(false)));
    }
}
