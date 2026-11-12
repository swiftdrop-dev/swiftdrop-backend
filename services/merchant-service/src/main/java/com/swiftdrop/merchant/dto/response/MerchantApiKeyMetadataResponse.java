package com.swiftdrop.merchant.dto.response;
import java.time.Instant;
import java.util.*;
import com.swiftdrop.merchant.entity.MerchantApiCredential;
public record MerchantApiKeyMetadataResponse(UUID id, String name, String publicId, Set<String> scopes,
    Instant createdAt, Instant expiresAt, Instant revokedAt, UUID replacementId, String state) {
    public static MerchantApiKeyMetadataResponse from(MerchantApiCredential key, Instant now) {
        return new MerchantApiKeyMetadataResponse(key.getId(),key.getName(),key.getPublicId(),key.getScopes(),
            key.getCreatedAt(),key.getExpiresAt(),key.getRevokedAt(),key.getReplacementId(),key.stateAt(now));
    }
}
