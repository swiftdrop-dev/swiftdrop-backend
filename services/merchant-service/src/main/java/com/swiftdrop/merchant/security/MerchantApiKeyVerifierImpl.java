package com.swiftdrop.merchant.security;

import com.swiftdrop.merchant.entity.Merchant;
import com.swiftdrop.merchant.entity.MerchantApiCredential;
import com.swiftdrop.merchant.entity.enums.MerchantStatus;
import com.swiftdrop.merchant.exception.MerchantOrderException;
import com.swiftdrop.merchant.repository.MerchantApiCredentialRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@Transactional(readOnly = true)
@ConditionalOnProperty(name = {"merchant.persistence.enabled", "merchant.orders.enabled"}, havingValue = "true")
public class MerchantApiKeyVerifierImpl implements MerchantApiKeyVerifier {
    private final MerchantApiCredentialRepository credentialRepository;

    public MerchantApiKeyVerifierImpl(MerchantApiCredentialRepository credentialRepository) {
        this.credentialRepository = credentialRepository;
    }

    @Override
    public VerifiedMerchantApiCredential verify(String apiKey, String requiredScope) {
        if (apiKey == null || apiKey.isBlank()) {
            throw invalidApiKey();
        }

        MerchantApiCredential credential = credentialRepository.findByKeyHash(hash(apiKey))
                .orElseThrow(MerchantApiKeyVerifierImpl::invalidApiKey);

        Instant expiresAt = credential.getExpiresAt();
        if (!Boolean.TRUE.equals(credential.getIsActive()) || credential.getRevokedAt() != null
                || (expiresAt != null && !expiresAt.isAfter(Instant.now()))) {
            throw invalidApiKey();
        }

        Merchant merchant = credential.getMerchant();
        if (merchant == null || merchant.getId() == null || merchant.getStatus() != MerchantStatus.APPROVED) {
            throw new MerchantOrderException(403, "MERCHANT_NOT_APPROVED", "Merchant must be approved");
        }

        String storedScopes = credential.getScopes();
        Set<String> scopes = storedScopes == null ? Set.of() : Arrays.stream(storedScopes.split(","))
                .map(String::trim)
                .filter(scope -> !scope.isEmpty())
                .collect(Collectors.toSet());

        boolean orderScope = "orders:read".equals(requiredScope) || "orders:write".equals(requiredScope);
        if (!orderScope || !scopes.contains(requiredScope)) {
            throw new MerchantOrderException(403, "INSUFFICIENT_SCOPE", "Required credential scope unavailable");
        }

        return new VerifiedMerchantApiCredential(merchant.getId(), credential.getId(), scopes);
    }

    private static MerchantOrderException invalidApiKey() {
        return new MerchantOrderException(401, "INVALID_API_KEY", "API credential is invalid");
    }

    private static String hash(String apiKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(apiKey.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
