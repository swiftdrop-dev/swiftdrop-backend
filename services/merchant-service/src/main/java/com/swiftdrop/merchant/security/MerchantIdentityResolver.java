package com.swiftdrop.merchant.security;
/** Resolve verified M1 identity; never trust request IDs or unverified token claims. */
public interface MerchantIdentityResolver {
    MerchantActor currentActor();
}
