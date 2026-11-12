package com.swiftdrop.merchant.security;
import java.util.*;
/** Machine principal only; must never be adapted to a human credential-management actor. */
public record VerifiedMerchantApiCredential(UUID merchantId, UUID credentialId, Set<String> scopes) {
    public VerifiedMerchantApiCredential { scopes=Set.copyOf(scopes); }
}
