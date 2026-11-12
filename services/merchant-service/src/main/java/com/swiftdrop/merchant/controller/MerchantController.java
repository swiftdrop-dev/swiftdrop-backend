package com.swiftdrop.merchant.controller;
import com.swiftdrop.merchant.dto.request.*;
import com.swiftdrop.merchant.dto.response.MerchantResponse;
import com.swiftdrop.merchant.service.MerchantService;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/v1/merchant")
@ConditionalOnProperty(name="merchant.persistence.enabled", havingValue="true")
public class MerchantController {
    private final MerchantService service;
    public MerchantController(MerchantService service) { this.service=service; }
    @PostMapping("/onboarding") public ResponseEntity<MerchantResponse> register(@Valid @RequestBody MerchantOnboardingRequest request) {
        return ResponseEntity.status(201).body(service.register(request));
    }
    @GetMapping("/me") public MerchantResponse profile() { return service.profile(); }
    @PatchMapping("/me") public MerchantResponse update(@Valid @RequestBody MerchantProfileUpdateRequest request) { return service.update(request); }
}
