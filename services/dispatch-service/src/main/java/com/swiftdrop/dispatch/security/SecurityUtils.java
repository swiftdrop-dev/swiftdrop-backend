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

    /** Returns the authenticated user/dispatcher/courier UUID, or fails closed if unmapped. */
    public static java.util.UUID requireMappedActorId() {
        UserPrincipal principal = requirePrincipal();
        if (principal.getId() == null) {
            throw new IdentityMappingUnavailableException();
        }
        return principal.getId();
    }
}
