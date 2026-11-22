package com.swiftdrop.pricing.controller;

import com.swiftdrop.pricing.dto.request.FeeEstimateRequest;
import com.swiftdrop.pricing.dto.response.FeeEstimateResponse;
import com.swiftdrop.pricing.service.PricingCalculationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/v1/pricing")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@Tag(name = "Pricing & Fee Estimation", description = "Dynamic shipping rate calculation using Strategy Pattern")
public class PricingEstimateController {

    private final PricingCalculationService calculationService;

    @PostMapping("/estimate")
    @Operation(summary = "Calculate shipping fee estimate", description = "Used by customer booking UI, merchant order API, and dispatcher")
    public ResponseEntity<FeeEstimateResponse> calculateEstimate(@Valid @RequestBody FeeEstimateRequest request) {
        FeeEstimateResponse response = calculationService.calculateFee(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Dedicated convenience endpoint for Member 9's AI Assistant & MCP Server.
     * Supports queries like: GET /api/v1/pricing/ai-tool/estimate?weightKg=2&fromZone=Colombo&toZone=Kandy
     */
    @GetMapping("/ai-tool/estimate")
    @Operation(summary = "AI Tool Fee Estimation", description = "Tool endpoint for Member 9's LLM Chatbot and MCP server")
    public ResponseEntity<FeeEstimateResponse> calculateForAiAssistant(
            @RequestParam BigDecimal weightKg,
            @RequestParam String fromZone,
            @RequestParam String toZone,
            @RequestParam(required = false, defaultValue = "STANDARD") String deliveryType) {

        FeeEstimateRequest request = FeeEstimateRequest.builder()
                .weightKg(weightKg)
                .fromZoneCode(fromZone)
                .toZoneCode(toZone)
                .deliveryType(deliveryType)
                .build();

        return ResponseEntity.ok(calculationService.calculateFee(request));
    }
}
