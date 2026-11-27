package com.swiftdrop.merchant.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import com.swiftdrop.merchant.exception.ResourceNotFoundException;
import com.swiftdrop.merchant.exception.UnauthorizedException;

import java.util.Optional;
import java.util.UUID;

public final class SecurityUtils {

    @FunctionalInterface
    public interface MerchantOwnerLookup {
        Optional<UUID> findOwnerUserId(UUID merchantId);
    }

    private static volatile MerchantOwnerLookup merchantOwnerLookup;

    private SecurityUtils() {
    }

    public static void bindMerchantOwnerLookup(MerchantOwnerLookup lookup) {
        merchantOwnerLookup = lookup;
    }

    static MerchantOwnerLookup currentMerchantOwnerLookup() {
        return merchantOwnerLookup;
    }

    public static Optional<UUID> getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserPrincipal principal) {
            return Optional.ofNullable(principal.getId());
        }
        return Optional.empty();
    }

    public static UUID requireCurrentUserId() {
        return getCurrentUserId().orElseThrow(() ->
                new UnauthorizedException("Authenticated user ID mapping is not configured."));
    }

    public static void requireMerchantAccess(UUID requestedMerchantId) {
        if (requestedMerchantId == null) {
            throw new UnauthorizedException("Merchant id is required.");
        }
        if (hasRole("ADMIN")) {
            return;
        }
        if (!hasRole("MERCHANT")) {
            throw new UnauthorizedException("Merchant role is required.");
        }
        UUID currentUserId = requireCurrentUserId();
        MerchantOwnerLookup lookup = merchantOwnerLookup;
        if (lookup == null) {
            throw new UnauthorizedException("Merchant authorization is not configured.");
        }
        UUID ownerUserId = lookup.findOwnerUserId(requestedMerchantId)
                .orElseThrow(() -> new ResourceNotFoundException("Merchant not found: " + requestedMerchantId));
        if (!currentUserId.equals(ownerUserId)) {
            throw new UnauthorizedException("You do not have permission to access this merchant.");
        }
    }

    public static UUID requireAdminUserId() {
        if (!hasRole("ADMIN")) {
            throw new UnauthorizedException("Administrator role is required.");
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
