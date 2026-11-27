package com.swiftdrop.parcel.security;

import com.swiftdrop.parcel.exception.AuthenticationRequiredException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Locale;
import java.util.Set;

/** Identity taken only from a validated JWT principal. */
public final class CurrentUser {

    private static final Set<String> PARCEL_READ_ROLES = Set.of("admin", "dispatcher", "staff");

    private CurrentUser() {
    }

    public static UserPrincipal require() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserPrincipal principal && principal.getId() != null) {
            return principal;
        }
        throw new AuthenticationRequiredException();
    }

    public static boolean canReadAnyParcel(UserPrincipal principal) {
        String role = principal.getRole();
        return role != null && PARCEL_READ_ROLES.contains(role.toLowerCase(Locale.ROOT));
    }

    public static boolean isCourier(UserPrincipal principal) {
        return principal.getRole() != null && "courier".equalsIgnoreCase(principal.getRole());
    }
}
