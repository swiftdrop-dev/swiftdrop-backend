package com.swiftdrop.pricing.dto.response;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ZoneRateDto {
    private Long id;

    @NotNull(message = "From zone is required")
    private Long fromZoneId;
    private String fromZoneName;

    @NotNull(message = "To zone is required")
    private Long toZoneId;
    private String toZoneName;

    @NotNull(message = "Base fee is required")
    @DecimalMin("0.0")
    private BigDecimal baseFee;

    @NotNull(message = "Per kg fee is required")
    @DecimalMin("0.0")
    private BigDecimal perKgFee;

    @NotNull(message = "Express multiplier is required")
    @DecimalMin("1.0")
    @Builder.Default
    private BigDecimal expressMultiplier = new BigDecimal("1.50");

    @NotNull(message = "Estimated days required")
    @Min(1)
    @Builder.Default
    private Integer estimatedDeliveryDays = 2;
}
