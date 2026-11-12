package com.swiftdrop.merchant.security;
import java.util.Set;
/** Only a trusted M1 adapter may construct actors from verified authentication. */
public record MerchantActor(String subject, Set<String> authorities) {
    public MerchantActor {
        if (subject == null || subject.isBlank() || subject.length() > 255)
            throw new IllegalArgumentException("A verified subject is required");
        authorities = Set.copyOf(authorities);
    }
}
