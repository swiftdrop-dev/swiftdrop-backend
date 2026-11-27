package com.swiftdrop.merchant.security;

import com.swiftdrop.merchant.exception.ResourceNotFoundException;
import com.swiftdrop.merchant.exception.UnauthorizedException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SecurityUtilsAuthorizationTest {

    private final Map<UUID, UUID> owners = new ConcurrentHashMap<>();
    private SecurityUtils.MerchantOwnerLookup previousLookup;

    @BeforeEach
    void rememberLookup() {
        previousLookup = SecurityUtils.currentMerchantOwnerLookup();
    }

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
        SecurityUtils.bindMerchantOwnerLookup(previousLookup);
    }

    @Test
    void merchantCanAccessOwnRecordOnly() {
        UUID merchantA = UUID.randomUUID();
        UUID merchantB = UUID.randomUUID();
        UUID userA = UUID.randomUUID();
        UUID userB = UUID.randomUUID();
        owners.put(merchantA, userA);
        owners.put(merchantB, userB);
        SecurityUtils.bindMerchantOwnerLookup(id -> Optional.ofNullable(owners.get(id)));

        authenticate(userA, "ROLE_MERCHANT");
        assertDoesNotThrow(() -> SecurityUtils.requireMerchantAccess(merchantA));
        assertThrows(UnauthorizedException.class, () -> SecurityUtils.requireMerchantAccess(merchantB));
    }

    @Test
    void adminCanAccessAnyMerchantAndCustomerCannot() {
        UUID merchantId = UUID.randomUUID();
        owners.put(merchantId, UUID.randomUUID());
        SecurityUtils.bindMerchantOwnerLookup(id -> Optional.ofNullable(owners.get(id)));

        authenticate(UUID.randomUUID(), "ROLE_ADMIN");
        assertDoesNotThrow(() -> SecurityUtils.requireMerchantAccess(merchantId));

        authenticate(UUID.randomUUID(), "ROLE_CUSTOMER");
        assertThrows(UnauthorizedException.class, () -> SecurityUtils.requireMerchantAccess(merchantId));
    }

    @Test
    void missingMerchantIsNotFoundAndUnboundLookupFailsClosed() {
        authenticate(UUID.randomUUID(), "ROLE_MERCHANT");
        SecurityUtils.bindMerchantOwnerLookup(id -> Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> SecurityUtils.requireMerchantAccess(UUID.randomUUID()));

        SecurityUtils.bindMerchantOwnerLookup(null);
        assertThrows(UnauthorizedException.class, () -> SecurityUtils.requireMerchantAccess(UUID.randomUUID()));
    }

    private void authenticate(UUID userId, String role) {
        UserPrincipal principal = new UserPrincipal(userId, "user@swiftdrop.test", role);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority(role))));
    }
}
