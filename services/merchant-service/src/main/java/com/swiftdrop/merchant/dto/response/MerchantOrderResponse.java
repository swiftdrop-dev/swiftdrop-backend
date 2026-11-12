package com.swiftdrop.merchant.dto.response;
import com.swiftdrop.merchant.dto.request.MerchantOrderCreateRequest;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
public record MerchantOrderResponse(UUID id,String merchantOrderRef,String trackingNumber,String status,
    MerchantOrderCreateRequest details,BigDecimal deliveryFee,BigDecimal totalFee,String currency,Instant createdAt) {
    @Override public String toString() { return "MerchantOrderResponse[REDACTED]"; }
}
