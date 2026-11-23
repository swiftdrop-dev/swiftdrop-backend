package com.swiftdrop.merchant.controller;

import com.swiftdrop.merchant.dto.request.BulkMerchantOrderRequest;
import com.swiftdrop.merchant.dto.request.MerchantOrderCreateRequest;
import com.swiftdrop.merchant.dto.response.BulkMerchantOrderResponse;
import com.swiftdrop.merchant.dto.response.MerchantOrderPageResponse;
import com.swiftdrop.merchant.dto.response.MerchantOrderResponse;
import com.swiftdrop.merchant.entity.Merchant;
import com.swiftdrop.merchant.security.SecurityUtils;
import com.swiftdrop.merchant.service.MerchantOrderService;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping({"/api/v1/merchants/orders", "/api/v1/merchant/orders"})
@CrossOrigin(origins = "*")
public class MerchantOrderController {

    private final MerchantOrderService merchantOrderService;

    public MerchantOrderController(MerchantOrderService merchantOrderService) {
        this.merchantOrderService = merchantOrderService;
    }

    @PostMapping
    public ResponseEntity<MerchantOrderResponse> createOrder(
            @RequestHeader(value = "X-API-Key", required = false) String xApiKey,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody MerchantOrderCreateRequest request) {

        UUID currentUserId = SecurityUtils.getCurrentUserId().orElse(null);
        Merchant merchant = merchantOrderService.resolveMerchant(xApiKey, authorization, currentUserId);
        MerchantOrderResponse response = merchantOrderService.createOrder(merchant, request, idempotencyKey);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/bulk")
    public ResponseEntity<BulkMerchantOrderResponse> createBulkOrders(
            @RequestHeader(value = "X-API-Key", required = false) String xApiKey,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @Valid @RequestBody BulkMerchantOrderRequest request) {

        UUID currentUserId = SecurityUtils.getCurrentUserId().orElse(null);
        Merchant merchant = merchantOrderService.resolveMerchant(xApiKey, authorization, currentUserId);
        BulkMerchantOrderResponse response = merchantOrderService.createBulkOrders(merchant, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<MerchantOrderPageResponse> listOrders(
            @RequestHeader(value = "X-API-Key", required = false) String xApiKey,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String merchantOrderRef,
            @RequestParam(required = false) String trackingNumber,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant createdFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant createdTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        UUID currentUserId = SecurityUtils.getCurrentUserId().orElse(null);
        Merchant merchant = merchantOrderService.resolveMerchant(xApiKey, authorization, currentUserId);

        int sanitizedPage = Math.max(0, page);
        int sanitizedSize = Math.max(1, Math.min(100, size));
        var pageable = PageRequest.of(sanitizedPage, sanitizedSize, Sort.by(Sort.Direction.DESC, "createdAt"));

        MerchantOrderPageResponse response = merchantOrderService.listOrders(
                merchant, status, merchantOrderRef, trackingNumber, createdFrom, createdTo, pageable);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<MerchantOrderResponse> getOrder(
            @RequestHeader(value = "X-API-Key", required = false) String xApiKey,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable String id) {

        UUID currentUserId = SecurityUtils.getCurrentUserId().orElse(null);
        Merchant merchant = merchantOrderService.resolveMerchant(xApiKey, authorization, currentUserId);
        MerchantOrderResponse response = merchantOrderService.getOrder(merchant, id);
        return ResponseEntity.ok(response);
    }
}
