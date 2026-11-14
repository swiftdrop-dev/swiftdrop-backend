package com.swiftdrop.merchant.controller;

import com.swiftdrop.merchant.dto.ApiResponse;
import com.swiftdrop.merchant.dto.request.AddMerchantAddressRequest;
import com.swiftdrop.merchant.dto.request.RegisterMerchantRequest;
import com.swiftdrop.merchant.dto.request.SetBankAccountRequest;
import com.swiftdrop.merchant.dto.request.UpdateMerchantProfileRequest;
import com.swiftdrop.merchant.dto.request.UpdateWebhookRequest;
import com.swiftdrop.merchant.dto.request.UploadDocumentRequest;
import com.swiftdrop.merchant.dto.response.MerchantAddressResponse;
import com.swiftdrop.merchant.dto.response.MerchantBankAccountResponse;
import com.swiftdrop.merchant.dto.response.MerchantDocumentResponse;
import com.swiftdrop.merchant.dto.response.MerchantResponse;
import com.swiftdrop.merchant.security.SecurityUtils;
import com.swiftdrop.merchant.service.MerchantOnboardingService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/merchants/onboarding")
public class MerchantOnboardingController {

    private final MerchantOnboardingService onboardingService;

    public MerchantOnboardingController(MerchantOnboardingService onboardingService) {
        this.onboardingService = onboardingService;
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<MerchantResponse>> register(@Valid @RequestBody RegisterMerchantRequest request) {
        UUID userId = SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new IllegalArgumentException("User ID is missing from security context or X-User-Id header"));
        MerchantResponse response = onboardingService.registerMerchant(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Merchant registered successfully in DRAFT status", response));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<MerchantResponse>> getMyProfile() {
        UUID userId = SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new IllegalArgumentException("User ID is missing from security context or X-User-Id header"));
        MerchantResponse response = onboardingService.getMerchantByUserId(userId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<MerchantResponse>> getById(@PathVariable("id") UUID id) {
        MerchantResponse response = onboardingService.getMerchantById(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PutMapping("/{id}/profile")
    public ResponseEntity<ApiResponse<MerchantResponse>> updateProfile(
            @PathVariable("id") UUID id,
            @Valid @RequestBody UpdateMerchantProfileRequest request) {
        MerchantResponse response = onboardingService.updateProfile(id, request);
        return ResponseEntity.ok(ApiResponse.success("Profile updated successfully", response));
    }

    @PostMapping("/{id}/addresses")
    public ResponseEntity<ApiResponse<MerchantAddressResponse>> addAddress(
            @PathVariable("id") UUID id,
            @Valid @RequestBody AddMerchantAddressRequest request) {
        MerchantAddressResponse response = onboardingService.addAddress(id, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Pickup address added successfully", response));
    }

    @PutMapping("/{id}/bank-account")
    public ResponseEntity<ApiResponse<MerchantBankAccountResponse>> setBankAccount(
            @PathVariable("id") UUID id,
            @Valid @RequestBody SetBankAccountRequest request) {
        MerchantBankAccountResponse response = onboardingService.setBankAccount(id, request);
        return ResponseEntity.ok(ApiResponse.success("Settlement bank account configured successfully", response));
    }

    @PostMapping("/{id}/documents")
    public ResponseEntity<ApiResponse<MerchantDocumentResponse>> uploadDocument(
            @PathVariable("id") UUID id,
            @Valid @RequestBody UploadDocumentRequest request) {
        MerchantDocumentResponse response = onboardingService.uploadDocument(id, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("KYC document uploaded successfully", response));
    }

    @PostMapping("/{id}/submit")
    public ResponseEntity<ApiResponse<MerchantResponse>> submit(@PathVariable("id") UUID id) {
        MerchantResponse response = onboardingService.submitForVerification(id);
        return ResponseEntity.ok(ApiResponse.success("Merchant profile submitted for administrative verification", response));
    }

    @PutMapping("/{id}/webhook")
    public ResponseEntity<ApiResponse<MerchantResponse>> updateWebhook(
            @PathVariable("id") UUID id,
            @Valid @RequestBody UpdateWebhookRequest request) {
        MerchantResponse response = onboardingService.updateWebhook(id, request);
        return ResponseEntity.ok(ApiResponse.success("Webhook endpoint updated", response));
    }
}
