package com.swiftdrop.merchant.controller;

import com.swiftdrop.merchant.dto.ApiResponse;
import com.swiftdrop.merchant.dto.request.GenerateApiKeyRequest;
import com.swiftdrop.merchant.dto.response.ApiKeyCreatedResponse;
import com.swiftdrop.merchant.dto.response.MerchantApiCredentialResponse;
import com.swiftdrop.merchant.security.SecurityUtils;
import com.swiftdrop.merchant.service.MerchantCredentialService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/merchants/{merchantId}/credentials")
public class MerchantCredentialController {

    private final MerchantCredentialService credentialService;

    public MerchantCredentialController(MerchantCredentialService credentialService) {
        this.credentialService = credentialService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ApiKeyCreatedResponse>> generateKey(
            @PathVariable("merchantId") UUID merchantId,
            @Valid @RequestBody GenerateApiKeyRequest request) {
        ApiKeyCreatedResponse response = credentialService.generateApiKey(merchantId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("API key generated successfully. Copy the key now as it cannot be retrieved again.", response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<MerchantApiCredentialResponse>>> listKeys(
            @PathVariable("merchantId") UUID merchantId) {
        List<MerchantApiCredentialResponse> credentials = credentialService.listCredentials(merchantId);
        return ResponseEntity.ok(ApiResponse.success(credentials));
    }

    @DeleteMapping("/{credentialId}")
    public ResponseEntity<ApiResponse<Void>> revokeKey(
            @PathVariable("merchantId") UUID merchantId,
            @PathVariable("credentialId") UUID credentialId,
            @RequestParam(value = "reason", required = false) String reason) {
        UUID revokedBy = SecurityUtils.getCurrentUserId()
                .orElse(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        credentialService.revokeCredential(merchantId, credentialId, revokedBy, reason);
        return ResponseEntity.ok(ApiResponse.success("API key revoked successfully", null));
    }
}
