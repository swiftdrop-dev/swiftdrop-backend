package com.swiftdrop.merchant.controller;
import com.swiftdrop.merchant.dto.request.*;
import com.swiftdrop.merchant.dto.response.*;
import com.swiftdrop.merchant.service.MerchantApiCredentialService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/v1/merchant/me/api-keys")
@ConditionalOnProperty(name="merchant.persistence.enabled",havingValue="true")
public class MerchantApiCredentialController {
    private final MerchantApiCredentialService service;
    public MerchantApiCredentialController(MerchantApiCredentialService service) { this.service=service; }
    @PostMapping public ResponseEntity<MerchantApiKeyIssuedResponse> issue(@Valid @RequestBody MerchantApiKeyIssueRequest request) {
        return ResponseEntity.status(201).cacheControl(CacheControl.noStore()).body(service.issue(request));
    }
    @GetMapping public ResponseEntity<MerchantApiKeyPageResponse> list(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.list(page,size));
    }
    @PostMapping("/{id}/rotate") public ResponseEntity<MerchantApiKeyIssuedResponse> rotate(@PathVariable UUID id,@Valid @RequestBody MerchantApiKeyRotationRequest request) {
        return ResponseEntity.status(201).cacheControl(CacheControl.noStore()).body(service.rotate(id,request));
    }
    @PostMapping("/{id}/revoke") public ResponseEntity<Void> revoke(@PathVariable UUID id) {
        service.revoke(id); return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
}
