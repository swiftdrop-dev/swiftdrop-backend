package com.swiftdrop.merchant.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import com.swiftdrop.merchant.exception.UnauthorizedException;

import java.util.Optional;
import java.util.UUID;

public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static Optional<UUID> getCurrentUserId() {
        // The current Auth Service JWT has an email subject and no user UUID claim.
        return Optional.empty();
    }

    public static UUID requireCurrentUserId() {
        return getCurrentUserId().orElseThrow(() ->
                new UnauthorizedException("Authenticated user ID mapping is not configured."));
    }

    public static void requireMerchantAccess(UUID requestedMerchantId) {
        if (!hasRole("MERCHANT")) {
            throw new UnauthorizedException("Merchant role is required.");
        }
        // No trusted User ID -> Merchant ID mapping exists; do not authorize by email or path ID alone.
        throw new UnauthorizedException("Merchant ownership mapping is not configured.");
    }

    public static UUID requireAdminUserId() {
        if (!hasRole("ADMIN")) {
            throw new UnauthorizedException("Administrator role is not available in the current JWT contract.");
        }
        return requireCurrentUserId();
    }

    public static Optional<UserPrincipal> getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserPrincipal principal) {
            return Optional.of(principal);
        }
        return Optional.empty();
    }

    public static boolean hasRole(String role) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return false;
        }
        String target = role.startsWith("ROLE_") ? role : "ROLE_" + role;
        return auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equalsIgnoreCase(target));
    }
}
