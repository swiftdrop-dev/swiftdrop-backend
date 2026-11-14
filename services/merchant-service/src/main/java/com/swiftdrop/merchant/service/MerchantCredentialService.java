package com.swiftdrop.merchant.service;

import com.swiftdrop.merchant.dto.request.GenerateApiKeyRequest;
import com.swiftdrop.merchant.dto.response.ApiKeyCreatedResponse;
import com.swiftdrop.merchant.dto.response.MerchantApiCredentialResponse;
import com.swiftdrop.merchant.entity.Merchant;
import com.swiftdrop.merchant.entity.MerchantApiCredential;
import com.swiftdrop.merchant.exception.ResourceNotFoundException;
import com.swiftdrop.merchant.mapper.MerchantMapper;
import com.swiftdrop.merchant.repository.MerchantApiCredentialRepository;
import com.swiftdrop.merchant.repository.MerchantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class MerchantCredentialService {

    private static final Logger log = LoggerFactory.getLogger(MerchantCredentialService.class);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final MerchantApiCredentialRepository credentialRepository;
    private final MerchantRepository merchantRepository;
    private final MerchantMapper merchantMapper;

    public MerchantCredentialService(MerchantApiCredentialRepository credentialRepository,
                                     MerchantRepository merchantRepository,
                                     MerchantMapper merchantMapper) {
        this.credentialRepository = credentialRepository;
        this.merchantRepository = merchantRepository;
        this.merchantMapper = merchantMapper;
    }

    @Transactional
    public ApiKeyCreatedResponse generateApiKey(UUID merchantId, GenerateApiKeyRequest request) {
        Merchant merchant = merchantRepository.findById(merchantId)
                .orElseThrow(() -> new ResourceNotFoundException("Merchant not found: " + merchantId));

        String env = request.getEnvironment() != null && request.getEnvironment().equalsIgnoreCase("SANDBOX")
                ? "SANDBOX" : "PRODUCTION";
        String envPrefix = env.equals("SANDBOX") ? "sk_test_" : "sk_live_";

        byte[] randomBytes = new byte[24];
        SECURE_RANDOM.nextBytes(randomBytes);
        String randomPart = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        String rawApiKey = envPrefix + randomPart;

        String keyPrefix = rawApiKey.substring(0, Math.min(16, rawApiKey.length()));
        String keyHash = hashKey(rawApiKey);

        MerchantApiCredential credential = new MerchantApiCredential();
        credential.setMerchant(merchant);
        credential.setKeyPrefix(keyPrefix);
        credential.setKeyHash(keyHash);
        credential.setEnvironment(env);
        credential.setLabel(request.getLabel() != null ? request.getLabel() : "Default API Key");
        credential.setScopes(request.getScopes() != null ? request.getScopes() : "parcels:create,parcels:read,tracking:read");
        credential.setIsActive(true);

        if (request.getExpiresInDays() != null && request.getExpiresInDays() > 0) {
            credential.setExpiresAt(Instant.now().plus(request.getExpiresInDays(), ChronoUnit.DAYS));
        }

        credentialRepository.save(credential);
        log.info("Generated new API key [id={}, prefix={}, env={}] for merchant {}",
                credential.getId(), keyPrefix, env, merchantId);

        return new ApiKeyCreatedResponse(
                credential.getId(),
                rawApiKey,
                keyPrefix,
                env,
                credential.getLabel(),
                credential.getScopes(),
                credential.getExpiresAt()
        );
    }

    @Transactional(readOnly = true)
    public List<MerchantApiCredentialResponse> listCredentials(UUID merchantId) {
        return credentialRepository.findByMerchantId(merchantId).stream()
                .map(merchantMapper::toApiCredentialResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public void revokeCredential(UUID merchantId, UUID credentialId, UUID revokedBy, String reason) {
        MerchantApiCredential credential = credentialRepository.findById(credentialId)
                .orElseThrow(() -> new ResourceNotFoundException("Credential not found: " + credentialId));

        if (!credential.getMerchant().getId().equals(merchantId)) {
            throw new ResourceNotFoundException("Credential does not belong to merchant: " + merchantId);
        }

        credential.setIsActive(false);
        credential.setRevokedAt(Instant.now());
        credential.setRevokedBy(revokedBy);
        credential.setRevocationReason(reason != null ? reason : "Revoked by user");
        credentialRepository.save(credential);

        log.info("Revoked API key [id={}, prefix={}] for merchant {}", credentialId, credential.getKeyPrefix(), merchantId);
    }

    @Transactional
    public Optional<Merchant> validateApiKey(String rawApiKey) {
        if (rawApiKey == null || rawApiKey.isBlank()) {
            return Optional.empty();
        }

        String keyHash = hashKey(rawApiKey);
        Optional<MerchantApiCredential> credentialOpt = credentialRepository.findByKeyHash(keyHash);

        if (credentialOpt.isEmpty()) {
            return Optional.empty();
        }

        MerchantApiCredential credential = credentialOpt.get();
        if (!Boolean.TRUE.equals(credential.getIsActive())) {
            return Optional.empty();
        }

        if (credential.getExpiresAt() != null && credential.getExpiresAt().isBefore(Instant.now())) {
            return Optional.empty();
        }

        // Record last used timestamp
        credential.setLastUsedAt(Instant.now());
        credentialRepository.save(credential);

        return Optional.of(credential.getMerchant());
    }

    private String hashKey(String rawKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawKey.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
