package com.swiftdrop.merchant.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.merchant.dto.request.RegisterWebhookEndpointRequest;
import com.swiftdrop.merchant.dto.request.UpdateWebhookEndpointRequest;
import com.swiftdrop.merchant.dto.response.WebhookDeliveryDetailResponse;
import com.swiftdrop.merchant.dto.response.WebhookDeliveryResponse;
import com.swiftdrop.merchant.dto.response.WebhookEndpointCreatedResponse;
import com.swiftdrop.merchant.dto.response.WebhookEndpointResponse;
import com.swiftdrop.merchant.entity.enums.WebhookDeliveryStatus;
import com.swiftdrop.merchant.exception.GlobalExceptionHandler;
import com.swiftdrop.merchant.exception.ResourceNotFoundException;
import com.swiftdrop.merchant.exception.UnauthorizedException;
import com.swiftdrop.merchant.service.webhook.MerchantWebhookService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class MerchantWebhookControllerTest {

    @Mock
    private MerchantWebhookService webhookService;

    @InjectMocks
    private MerchantWebhookController controller;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private UUID merchantId;
    private UUID endpointId;
    private UUID deliveryId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .build();

        merchantId = UUID.randomUUID();
        endpointId = UUID.randomUUID();
        deliveryId = UUID.randomUUID();
    }

    @Test
    void shouldRegisterEndpointAndReturn201WithSecret() throws Exception {
        RegisterWebhookEndpointRequest request = new RegisterWebhookEndpointRequest(
                "https://api.store.com/webhooks",
                "Primary order notifications",
                List.of("PARCEL_CREATED", "PARCEL_DELIVERED")
        );

        WebhookEndpointCreatedResponse response = new WebhookEndpointCreatedResponse(
                endpointId,
                merchantId,
                request.getUrl(),
                "whsec_************************abcd",
                request.getDescription(),
                true,
                request.getSubscribedEvents(),
                Instant.now(),
                Instant.now(),
                "whsec_full_raw_secret_returned_on_creation_abcd"
        );

        when(webhookService.registerEndpoint(eq(merchantId), any(RegisterWebhookEndpointRequest.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/v1/merchants/{merchantId}/webhooks", merchantId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(endpointId.toString()))
                .andExpect(jsonPath("$.data.url").value("https://api.store.com/webhooks"))
                .andExpect(jsonPath("$.data.secret").value("whsec_full_raw_secret_returned_on_creation_abcd"))
                .andExpect(jsonPath("$.data.maskedSecret").value("whsec_************************abcd"));
    }

    @Test
    void shouldRejectRegistrationWithBlankUrl() throws Exception {
        RegisterWebhookEndpointRequest invalidRequest = new RegisterWebhookEndpointRequest(
                "",
                "Invalid empty url",
                List.of()
        );

        mockMvc.perform(post("/api/v1/merchants/{merchantId}/webhooks", merchantId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldListEndpointsWithMaskedSecrets() throws Exception {
        WebhookEndpointResponse ep = new WebhookEndpointResponse(
                endpointId,
                merchantId,
                "https://api.store.com/hooks",
                "whsec_************************9999",
                "Main Hook",
                true,
                List.of("PARCEL_CREATED"),
                Instant.now(),
                Instant.now()
        );

        when(webhookService.listEndpoints(merchantId)).thenReturn(List.of(ep));

        mockMvc.perform(get("/api/v1/merchants/{merchantId}/webhooks", merchantId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].id").value(endpointId.toString()))
                .andExpect(jsonPath("$.data[0].maskedSecret").value("whsec_************************9999"))
                .andExpect(jsonPath("$.data[0].secret").doesNotExist());
    }

    @Test
    void shouldGetEndpointById() throws Exception {
        WebhookEndpointResponse ep = new WebhookEndpointResponse(
                endpointId,
                merchantId,
                "https://api.store.com/hooks",
                "whsec_************************9999",
                "Main Hook",
                true,
                List.of("PARCEL_CREATED"),
                Instant.now(),
                Instant.now()
        );

        when(webhookService.getEndpoint(merchantId, endpointId)).thenReturn(ep);

        mockMvc.perform(get("/api/v1/merchants/{merchantId}/webhooks/{endpointId}", merchantId, endpointId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(endpointId.toString()));
    }

    @Test
    void shouldUpdateEndpoint() throws Exception {
        UpdateWebhookEndpointRequest req = new UpdateWebhookEndpointRequest(
                "https://api.store.com/new-hook",
                "Updated Hook",
                List.of("PARCEL_DELIVERED"),
                true
        );

        WebhookEndpointResponse res = new WebhookEndpointResponse(
                endpointId,
                merchantId,
                req.getUrl(),
                "whsec_************************9999",
                req.getDescription(),
                true,
                req.getSubscribedEvents(),
                Instant.now(),
                Instant.now()
        );

        when(webhookService.updateEndpoint(eq(merchantId), eq(endpointId), any(UpdateWebhookEndpointRequest.class)))
                .thenReturn(res);

        mockMvc.perform(put("/api/v1/merchants/{merchantId}/webhooks/{endpointId}", merchantId, endpointId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.url").value("https://api.store.com/new-hook"));
    }

    @Test
    void shouldToggleEndpoint() throws Exception {
        WebhookEndpointResponse res = new WebhookEndpointResponse(
                endpointId,
                merchantId,
                "https://api.store.com/hook",
                "whsec_***",
                "Hook",
                false,
                List.of(),
                Instant.now(),
                Instant.now()
        );

        when(webhookService.toggleEndpoint(merchantId, endpointId)).thenReturn(res);

        mockMvc.perform(patch("/api/v1/merchants/{merchantId}/webhooks/{endpointId}/toggle", merchantId, endpointId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active").value(false));
    }

    @Test
    void shouldDeleteEndpoint() throws Exception {
        mockMvc.perform(delete("/api/v1/merchants/{merchantId}/webhooks/{endpointId}", merchantId, endpointId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(webhookService).deleteEndpoint(merchantId, endpointId);
    }

    @Test
    void shouldRotateSecretAndReturnNewSecret() throws Exception {
        WebhookEndpointCreatedResponse res = new WebhookEndpointCreatedResponse(
                endpointId,
                merchantId,
                "https://api.store.com/hook",
                "whsec_************************zzzz",
                "Hook",
                true,
                List.of(),
                Instant.now(),
                Instant.now(),
                "whsec_brand_new_rotated_secret_zzzz"
        );

        when(webhookService.rotateSecret(merchantId, endpointId)).thenReturn(res);

        mockMvc.perform(post("/api/v1/merchants/{merchantId}/webhooks/{endpointId}/rotate-secret", merchantId, endpointId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.secret").value("whsec_brand_new_rotated_secret_zzzz"));
    }

    @Test
    void shouldGetDeliveriesList() throws Exception {
        WebhookDeliveryResponse delivery = new WebhookDeliveryResponse(
                deliveryId,
                endpointId,
                "https://api.store.com/hook",
                merchantId,
                "evt-100",
                "PARCEL_OUT_FOR_DELIVERY",
                "TRK8888",
                "pcl-88",
                WebhookDeliveryStatus.SUCCESS,
                1,
                5,
                null,
                200,
                null,
                Instant.now(),
                Instant.now()
        );

        when(webhookService.getDeliveries(eq(merchantId), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(delivery), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/merchants/{merchantId}/webhooks/deliveries", merchantId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].eventId").value("evt-100"))
                .andExpect(jsonPath("$.data.content[0].status").value("SUCCESS"));
    }

    @Test
    void shouldGetDeliveryDetail() throws Exception {
        WebhookDeliveryResponse base = new WebhookDeliveryResponse(
                deliveryId,
                endpointId,
                "https://api.store.com/hook",
                merchantId,
                "evt-100",
                "PARCEL_OUT_FOR_DELIVERY",
                "TRK8888",
                "pcl-88",
                WebhookDeliveryStatus.SUCCESS,
                1,
                5,
                null,
                200,
                null,
                Instant.now(),
                Instant.now()
        );

        WebhookDeliveryDetailResponse detail = new WebhookDeliveryDetailResponse(
                base,
                "{\"eventId\":\"evt-100\",\"status\":\"OUT_FOR_DELIVERY\"}",
                Collections.emptyList()
        );

        when(webhookService.getDeliveryDetail(merchantId, deliveryId)).thenReturn(detail);

        mockMvc.perform(get("/api/v1/merchants/{merchantId}/webhooks/deliveries/{deliveryId}", merchantId, deliveryId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.payload").value("{\"eventId\":\"evt-100\",\"status\":\"OUT_FOR_DELIVERY\"}"));
    }

    @Test
    void shouldRetryDelivery() throws Exception {
        WebhookDeliveryResponse retried = new WebhookDeliveryResponse(
                deliveryId,
                endpointId,
                "https://api.store.com/hook",
                merchantId,
                "evt-100",
                "PARCEL_OUT_FOR_DELIVERY",
                "TRK8888",
                "pcl-88",
                WebhookDeliveryStatus.PENDING,
                2,
                5,
                null,
                null,
                null,
                Instant.now(),
                null
        );

        when(webhookService.retryDelivery(merchantId, deliveryId)).thenReturn(retried);

        mockMvc.perform(post("/api/v1/merchants/{merchantId}/webhooks/deliveries/{deliveryId}/retry", merchantId, deliveryId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.attemptCount").value(2));
    }

    @Test
    void shouldReturn403WhenUnauthorizedAccess() throws Exception {
        when(webhookService.listEndpoints(merchantId))
                .thenThrow(new UnauthorizedException("You do not have permission to access resources for this merchant."));

        mockMvc.perform(get("/api/v1/merchants/{merchantId}/webhooks", merchantId))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("You do not have permission to access resources for this merchant."));
    }

    @Test
    void shouldReturn404WhenNotFound() throws Exception {
        when(webhookService.getEndpoint(merchantId, endpointId))
                .thenThrow(new ResourceNotFoundException("Webhook endpoint not found"));

        mockMvc.perform(get("/api/v1/merchants/{merchantId}/webhooks/{endpointId}", merchantId, endpointId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }
}
