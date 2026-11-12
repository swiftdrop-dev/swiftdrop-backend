package com.swiftdrop.merchant.idempotency;
import com.swiftdrop.merchant.entity.MerchantOrderRequest;
public record MerchantOrderClaim(MerchantOrderRequest request,boolean replay) {}
