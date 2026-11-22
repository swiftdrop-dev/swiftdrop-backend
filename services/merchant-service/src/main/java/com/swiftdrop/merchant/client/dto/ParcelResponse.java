package com.swiftdrop.merchant.client.dto;
import com.swiftdrop.merchant.dto.response.MerchantOrderResponse;
import java.util.UUID;
public record ParcelResponse(UUID merchantId,MerchantOrderResponse order) {
    @Override public String toString() { return "ParcelResponse[REDACTED]"; }
}
