package com.swiftdrop.pricing.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Request payload to estimate delivery cost")
public class FeeEstimateRequest {

    @Schema(description = "Origin Zone ID (or pass fromZoneCode instead)", example = "1")
    private Long fromZoneId;

    @Schema(description = "Destination Zone ID (or pass toZoneCode instead)", example = "3")
    private Long toZoneId;

    @Schema(description = "Origin Zone Code (optional fallback)", example = "ZONE_WP_COLOMBO")
    private String fromZoneCode;

    @Schema(description = "Destination Zone Code (optional fallback)", example = "ZONE_CP_KANDY")
    private String toZoneCode;

    @NotNull(message = "Weight is required")
    @DecimalMin(value = "0.05", message = "Weight must be at least 0.05 kg")
    @Schema(description = "Weight in kilograms", example = "2.0")
    private BigDecimal weightKg;

    @Schema(description = "Delivery type: STANDARD, EXPRESS, or BULK", example = "STANDARD", defaultValue = "STANDARD")
    @Builder.Default
    private String deliveryType = "STANDARD";
}
