package com.swiftdrop.merchant.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record MerchantOrderResponse(
        UUID id,
        String merchantOrderRef,
        String trackingNumber,
        String parcelId,
        String status,
        MerchantOrderDetailsDto details,
        BigDecimal deliveryFee,
        BigDecimal totalFee,
        String currency,
        Instant createdAt,
        Instant updatedAt
) {}
