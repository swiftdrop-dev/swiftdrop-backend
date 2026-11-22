package com.swiftdrop.merchant.security;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record VerifiedMerchantApiCredential(UUID merchantId, UUID credentialId, Set<String> scopes) {
    public VerifiedMerchantApiCredential {
        Objects.requireNonNull(merchantId, "merchantId");
        Objects.requireNonNull(credentialId, "credentialId");
        scopes = Set.copyOf(Objects.requireNonNull(scopes, "scopes"));
    }
}
