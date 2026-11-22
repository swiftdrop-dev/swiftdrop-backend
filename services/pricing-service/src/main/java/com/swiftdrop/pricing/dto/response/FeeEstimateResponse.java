package com.swiftdrop.pricing.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Calculated fee estimation breakdown")
public class FeeEstimateResponse {

    @Schema(description = "Origin zone name", example = "Western Province (Colombo)")
    private String fromZoneName;

    @Schema(description = "Destination zone name", example = "Central Province (Kandy)")
    private String toZoneName;

    @Schema(description = "Base rate for this zone pair", example = "500.00")
    private BigDecimal baseFee;

    @Schema(description = "Charge per kg for this zone pair", example = "80.00")
    private BigDecimal perKgFee;

    @Schema(description = "Submitted parcel weight in kg", example = "2.0")
    private BigDecimal weightKg;

    @Schema(description = "Delivery type applied", example = "STANDARD")
    private String deliveryType;

    @Schema(description = "Applied strategy description", example = "Standard Door-to-Door Delivery")
    private String strategyApplied;

    @Schema(description = "Final calculated delivery fee in LKR", example = "660.00")
    private BigDecimal totalEstimatedFee;

    @Schema(description = "Estimated transit time in business days", example = "2")
    private Integer estimatedDeliveryDays;
}
