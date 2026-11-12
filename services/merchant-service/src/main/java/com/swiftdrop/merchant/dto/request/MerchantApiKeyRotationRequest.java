package com.swiftdrop.merchant.dto.request;
import java.time.Instant;
public record MerchantApiKeyRotationRequest(Instant expiresAt) {}
