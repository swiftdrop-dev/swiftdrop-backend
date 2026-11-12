package com.swiftdrop.merchant.security;
import com.swiftdrop.merchant.exception.MerchantException;
public final class UnavailableMerchantIdentityResolver implements MerchantIdentityResolver {
    @Override public MerchantActor currentActor() {
        throw new MerchantException(503, "IDENTITY_UNAVAILABLE", "M1 identity integration is not available");
    }
}
