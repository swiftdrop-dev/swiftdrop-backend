package com.swiftdrop.merchant.controller;
import com.swiftdrop.merchant.dto.request.MerchantReviewRequest;
import com.swiftdrop.merchant.dto.response.MerchantResponse;
import com.swiftdrop.merchant.service.MerchantService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/v1/admin/merchants")
@ConditionalOnProperty(name="merchant.persistence.enabled", havingValue="true")
public class MerchantReviewController {
    private final MerchantService service;
    public MerchantReviewController(MerchantService service) { this.service=service; }
    @PostMapping("/{id}/review") public MerchantResponse review(@PathVariable UUID id, @Valid @RequestBody MerchantReviewRequest request) {
        return service.review(id, request);
    }
}
