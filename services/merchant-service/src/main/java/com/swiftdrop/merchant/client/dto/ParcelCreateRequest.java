package com.swiftdrop.merchant.client.dto;
import com.swiftdrop.merchant.dto.request.MerchantOrderCreateRequest;
public record ParcelCreateRequest(MerchantOrderCreateRequest order) {
    @Override public String toString() { return "ParcelCreateRequest[REDACTED]"; }
}
