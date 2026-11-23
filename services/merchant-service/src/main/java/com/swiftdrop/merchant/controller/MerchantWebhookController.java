package com.swiftdrop.merchant.controller;

import com.swiftdrop.merchant.dto.ApiResponse;
import com.swiftdrop.merchant.dto.request.RegisterWebhookEndpointRequest;
import com.swiftdrop.merchant.dto.request.UpdateWebhookEndpointRequest;
import com.swiftdrop.merchant.dto.response.WebhookDeliveryDetailResponse;
import com.swiftdrop.merchant.dto.response.WebhookDeliveryResponse;
import com.swiftdrop.merchant.dto.response.WebhookEndpointCreatedResponse;
import com.swiftdrop.merchant.dto.response.WebhookEndpointResponse;
import com.swiftdrop.merchant.entity.enums.WebhookDeliveryStatus;
import com.swiftdrop.merchant.service.webhook.MerchantWebhookService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/merchants/{merchantId}/webhooks")
public class MerchantWebhookController {

    private final MerchantWebhookService webhookService;

    public MerchantWebhookController(MerchantWebhookService webhookService) {
        this.webhookService = webhookService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<WebhookEndpointCreatedResponse>> registerEndpoint(
            @PathVariable("merchantId") UUID merchantId,
            @Valid @RequestBody RegisterWebhookEndpointRequest request) {
        WebhookEndpointCreatedResponse response = webhookService.registerEndpoint(merchantId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Webhook endpoint registered successfully. Save the signing secret securely.", response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<WebhookEndpointResponse>>> listEndpoints(
            @PathVariable("merchantId") UUID merchantId) {
        List<WebhookEndpointResponse> endpoints = webhookService.listEndpoints(merchantId);
        return ResponseEntity.ok(ApiResponse.success(endpoints));
    }

    @GetMapping("/{endpointId}")
    public ResponseEntity<ApiResponse<WebhookEndpointResponse>> getEndpoint(
            @PathVariable("merchantId") UUID merchantId,
            @PathVariable("endpointId") UUID endpointId) {
        WebhookEndpointResponse response = webhookService.getEndpoint(merchantId, endpointId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PutMapping("/{endpointId}")
    public ResponseEntity<ApiResponse<WebhookEndpointResponse>> updateEndpoint(
            @PathVariable("merchantId") UUID merchantId,
            @PathVariable("endpointId") UUID endpointId,
            @Valid @RequestBody UpdateWebhookEndpointRequest request) {
        WebhookEndpointResponse response = webhookService.updateEndpoint(merchantId, endpointId, request);
        return ResponseEntity.ok(ApiResponse.success("Webhook endpoint updated successfully", response));
    }

    @PatchMapping("/{endpointId}/toggle")
    public ResponseEntity<ApiResponse<WebhookEndpointResponse>> toggleEndpoint(
            @PathVariable("merchantId") UUID merchantId,
            @PathVariable("endpointId") UUID endpointId) {
        WebhookEndpointResponse response = webhookService.toggleEndpoint(merchantId, endpointId);
        return ResponseEntity.ok(ApiResponse.success("Webhook endpoint status updated", response));
    }

    @DeleteMapping("/{endpointId}")
    public ResponseEntity<ApiResponse<Void>> deleteEndpoint(
            @PathVariable("merchantId") UUID merchantId,
            @PathVariable("endpointId") UUID endpointId) {
        webhookService.deleteEndpoint(merchantId, endpointId);
        return ResponseEntity.ok(ApiResponse.success("Webhook endpoint deleted successfully", null));
    }

    @PostMapping("/{endpointId}/rotate-secret")
    public ResponseEntity<ApiResponse<WebhookEndpointCreatedResponse>> rotateSecret(
            @PathVariable("merchantId") UUID merchantId,
            @PathVariable("endpointId") UUID endpointId) {
        WebhookEndpointCreatedResponse response = webhookService.rotateSecret(merchantId, endpointId);
        return ResponseEntity.ok(ApiResponse.success("Webhook secret rotated successfully. Save the new signing secret securely.", response));
    }

    @GetMapping("/deliveries")
    public ResponseEntity<ApiResponse<Page<WebhookDeliveryResponse>>> getDeliveries(
            @PathVariable("merchantId") UUID merchantId,
            @RequestParam(value = "eventType", required = false) String eventType,
            @RequestParam(value = "status", required = false) WebhookDeliveryStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<WebhookDeliveryResponse> deliveries = webhookService.getDeliveries(merchantId, eventType, status, pageable);
        return ResponseEntity.ok(ApiResponse.success(deliveries));
    }

    @GetMapping("/deliveries/{deliveryId}")
    public ResponseEntity<ApiResponse<WebhookDeliveryDetailResponse>> getDeliveryDetail(
            @PathVariable("merchantId") UUID merchantId,
            @PathVariable("deliveryId") UUID deliveryId) {
        WebhookDeliveryDetailResponse detail = webhookService.getDeliveryDetail(merchantId, deliveryId);
        return ResponseEntity.ok(ApiResponse.success(detail));
    }

    @PostMapping("/deliveries/{deliveryId}/retry")
    public ResponseEntity<ApiResponse<WebhookDeliveryResponse>> retryDelivery(
            @PathVariable("merchantId") UUID merchantId,
            @PathVariable("deliveryId") UUID deliveryId) {
        WebhookDeliveryResponse response = webhookService.retryDelivery(merchantId, deliveryId);
        return ResponseEntity.ok(ApiResponse.success("Webhook delivery retry initiated", response));
    }
}
