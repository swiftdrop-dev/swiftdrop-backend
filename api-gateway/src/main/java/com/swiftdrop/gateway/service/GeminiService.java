package com.swiftdrop.gateway.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Service
public class GeminiService {

    private static final Logger logger = LoggerFactory.getLogger(GeminiService.class);

    @Value("${gemini.api-key:}")
    private String apiKey;

    @Value("${gemini.model:gemini-1.5-flash}")
    private String model;

    @Value("${gemini.api-url:https://generativelanguage.googleapis.com/v1beta/models}")
    private String apiUrl;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public GeminiService(ObjectMapper objectMapper) {
        this.restTemplate = new RestTemplate();
        this.objectMapper = objectMapper;
    }

    /**
     * Checks if Gemini API key is configured.
     */
    public boolean isConfigured() {
        return apiKey != null && !apiKey.trim().isEmpty() && !apiKey.equals("YOUR_GEMINI_API_KEY_HERE");
    }

    /**
     * Queries Google Gemini with the user message and real-time database context.
     *
     * @param databaseContext Live records from SwiftDrop DB (parcels, couriers, status).
     * @param userMessage     The prompt typed by the user.
     * @return Generated reply or empty if unconfigured / error.
     */
    public Optional<String> generateResponse(String userMessage) {
        if (!isConfigured()) {
            logger.debug("Gemini API key is not configured, skipping external LLM call.");
            return Optional.empty();
        }

        try {
            String endpoint = String.format("%s/%s:generateContent", apiUrl, model);

            String systemPrompt = "You are SwiftBot, a general information assistant for SwiftDrop in Sri Lanka. "
                    + "Answer general questions about delivery services, booking, rates, and support. "
                    + "You do not have access to parcel records, recipient information, courier identity, or account data. "
                    + "For parcel-specific information, direct the user to the authenticated tracking or shipment view. "
                    + "Do not claim to have checked live records. Reply politely and clearly.";

            // Construct Gemini Request Body
            Map<String, Object> requestPayload = new LinkedHashMap<>();

            // System Instruction
            Map<String, Object> systemPart = Map.of("text", systemPrompt);
            requestPayload.put("system_instruction", Map.of("parts", List.of(systemPart)));

            // User Content
            Map<String, Object> userPart = Map.of("text", userMessage);
            Map<String, Object> contentMap = Map.of("role", "user", "parts", List.of(userPart));
            requestPayload.put("contents", List.of(contentMap));

            // Generation Config
            Map<String, Object> genConfig = Map.of(
                    "temperature", 0.3,
                    "maxOutputTokens", 800
            );
            requestPayload.put("generationConfig", genConfig);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("x-goog-api-key", apiKey.trim());

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestPayload, headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    endpoint,
                    HttpMethod.POST,
                    entity,
                    String.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                JsonNode candidates = root.path("candidates");
                if (candidates.isArray() && !candidates.isEmpty()) {
                    JsonNode textNode = candidates.get(0).path("content").path("parts").get(0).path("text");
                    if (!textNode.isMissingNode()) {
                        return Optional.of(textNode.asText().trim());
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("Gemini API call failed; using the local response engine [errorType={}]",
                    e.getClass().getSimpleName());
        }

        return Optional.empty();
    }
}
