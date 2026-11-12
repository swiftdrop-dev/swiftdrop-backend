package com.swiftdrop.merchant.config;
import com.swiftdrop.merchant.exception.MerchantException;
import java.time.*;
import java.util.Set;
public final class MerchantCredentialPolicy {
    public static final Set<String> ALLOWED_SCOPES=Set.of("orders:read","orders:write");
    public Instant expiration(Instant requested, Instant now) {
        Instant max=now.plus(Duration.ofDays(90));
        Instant expiry=requested == null ? max : requested;
        if (!expiry.isAfter(now) || expiry.isAfter(max)) throw invalid();
        return expiry;
    }
    public void scopes(Set<String> scopes) {
        if (scopes == null || scopes.isEmpty() || scopes.stream().anyMatch(s -> !ALLOWED_SCOPES.contains(s))) throw invalid();
    }
    private MerchantException invalid() { return new MerchantException(400,"INVALID_REQUEST","Invalid credential policy request"); }
}
