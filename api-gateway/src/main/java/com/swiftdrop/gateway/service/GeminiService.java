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
    public Optional<String> generateResponse(String databaseContext, String userMessage) {
        if (!isConfigured()) {
            logger.debug("Gemini API key is not configured, skipping external LLM call.");
            return Optional.empty();
        }

        try {
            String endpoint = String.format("%s/%s:generateContent?key=%s", apiUrl, model, apiKey.trim());

            String systemPrompt = "You are 'SwiftBot', the official intelligent AI customer assistant for SwiftDrop Courier & Delivery Platform in Sri Lanka.\n"
                    + "Your mission is to help customers, merchants, couriers, and dispatchers with their parcels, tracking, bookings, rates, and courier operations.\n\n"
                    + "CRITICAL INSTRUCTIONS:\n"
                    + "1. ALWAYS refer to the LIVE DATABASE CONTEXT provided below to answer parcel status, tracking numbers, couriers, or user questions accurately.\n"
                    + "2. If the user asks about 'my parcels' or 'where is my parcel', look at the CURRENT LOGGED-IN USER PARCELS section in the context.\n"
                    + "3. If a tracking number is mentioned (e.g. SWF-2024-xxxx), give clear details on current status, estimated delivery, courier, and destination.\n"
                    + "4. Respond politely, professionally, and clearly. You can understand and respond in English or friendly Sinhala/Singlish depending on user's language.\n"
                    + "5. If information is not in the database, clearly state so without hallucinating fake tracking details.\n\n"
                    + databaseContext;

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
            logger.warn("Gemini API call failed: {}. Falling back to domain rule engine.", e.getMessage());
        }

        return Optional.empty();
    }
}
