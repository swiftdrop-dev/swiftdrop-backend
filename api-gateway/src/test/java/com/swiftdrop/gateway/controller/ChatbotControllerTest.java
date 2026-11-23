package com.swiftdrop.gateway.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.gateway.dto.request.ChatbotMessageRequest;
import com.swiftdrop.gateway.service.ChatbotService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import com.swiftdrop.gateway.service.GeminiService;
import java.util.Optional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ChatbotControllerTest {

    private static final String SECRET = "test-only-gateway-jwt-key-material-at-least-32-bytes";
    private static final String SUBJECT = "chatbot-user@example.test";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private ChatbotService chatbotService;
    @MockitoBean private GeminiService geminiService;

    @BeforeEach
    void setUp() {
        chatbotService.resetUsage("remote-ip:127.0.0.1");
        chatbotService.resetUsage("jwt-subject:" + SUBJECT);
        org.mockito.Mockito.when(geminiService.generateResponse(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(Optional.empty());
    }

    @Test
    void shouldReturnGreetingWhenAskedAndIgnoreCallerSuppliedBodyIdentity() throws Exception {
        ChatbotMessageRequest request = new ChatbotMessageRequest("Hello SwiftBot");
        mockMvc.perform(post("/api/v1/chatbot/message")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .header("X-User-Id", "caller-controlled-user")
                        .header("X-User-Roles", "ROLE_ADMIN")
                        .header("X-User-Email", "attacker@example.test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply", containsString("SwiftBot")))
                .andExpect(jsonPath("$.remainingUses", is(3)))
                .andExpect(jsonPath("$.totalLimit", is(4)));
    }

    @Test
    void clientIdentityValuesCannotSelectQuotaOrParcelOwner() throws Exception {
        String request = "{\"message\":\"Where is parcel SWF-2024-001234?\",\"userId\":\"victim-uuid\"}";
        mockMvc.perform(post("/api/v1/chatbot/message")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-User-Id", "victim-uuid")
                        .header("X-User-Roles", "ROLE_ADMIN")
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply", containsString("not available through chat")))
                .andExpect(jsonPath("$.reply", org.hamcrest.Matchers.not(containsString("Tracking Update"))));
    }

    @Test
    void shouldEnforceMessageLimitAtomicallyPerServerDerivedSource() throws Exception {
        ChatbotMessageRequest request = new ChatbotMessageRequest("How do I track a parcel?");
        for (int i = 0; i < 4; i++) {
            mockMvc.perform(post("/api/v1/chatbot/message")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request))
                            .header("X-User-Id", "caller-" + i))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(post("/api/v1/chatbot/message")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .header("X-User-Id", "different-caller"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.status", is(429)));
    }

    @Test
    void usageRequiresValidatedJwtAndIgnoresCallerIdentityInputs() throws Exception {
        mockMvc.perform(get("/api/v1/chatbot/usage").param("userId", "victim-uuid")
                        .header("X-User-Id", "victim-uuid"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/chatbot/message")
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"Hello\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/chatbot/usage")
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .param("userId", "victim-uuid")
                        .header("X-User-Id", "victim-uuid"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usedCount", is(1)))
                .andExpect(jsonPath("$.userId").doesNotExist());
    }

    @Test
    void quotaResetEndpointIsDeniedEvenWithValidJwt() throws Exception {
        mockMvc.perform(post("/api/v1/chatbot/reset")
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .param("userId", "victim-uuid")
                        .header("X-User-Id", "victim-uuid"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/chatbot/reset"))
                .andExpect(status().isForbidden());
    }

    private static String bearer() {
        return "Bearer " + Jwts.builder()
                .subject(SUBJECT)
                .claim("role", "customer")
                .issuedAt(new Date())
                .expiration(Date.from(Instant.now().plusSeconds(300)))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }
}
