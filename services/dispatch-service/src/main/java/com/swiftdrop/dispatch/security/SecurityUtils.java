package com.swiftdrop.dispatch.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class SecurityUtils {

    private SecurityUtils() { }

    public static UserPrincipal requirePrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof UserPrincipal principal) {
            return principal;
        }
        throw new IdentityMappingUnavailableException();
    }

    /** The JWT identifies the user by email only; no authoritative UUID mapping is implemented. */
    public static java.util.UUID requireMappedActorId() {
        throw new IdentityMappingUnavailableException();
    }
}
