package com.swiftdrop.merchant.dto.response;
import com.swiftdrop.merchant.dto.request.MerchantOrderCreateRequest;
import java.math.BigDecimal;
import java.time.Instant;
public record MerchantOrderResponse(String id,String merchantOrderRef,String trackingNumber,String status,
    MerchantOrderCreateRequest details,BigDecimal deliveryFee,BigDecimal totalFee,String currency,Instant createdAt) {
    @Override public String toString() { return "MerchantOrderResponse[REDACTED]"; }
}
