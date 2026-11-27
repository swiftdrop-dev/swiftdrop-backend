package com.swiftdrop.merchant.service.webhook;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

@Service
public class WebhookSecretCryptoService {

    private static final Logger log = LoggerFactory.getLogger(WebhookSecretCryptoService.class);
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH_BYTES = 12; // 96 bits recommended for GCM
    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final String ENCRYPTED_PREFIX = "enc:v1:";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final SecretKey secretKey;

    public WebhookSecretCryptoService(
            @Value("${merchant.webhook.encryption.key:}") String configuredKey) {
        this.secretKey = initializeKey(configuredKey);
    }

    private SecretKey initializeKey(String configuredKey) {
        if (configuredKey == null || configuredKey.isBlank()) {
            throw new IllegalStateException("MERCHANT_WEBHOOK_ENCRYPTION_KEY must be configured");
        }
        byte[] keyBytes = parseKey(configuredKey.trim());
        return new SecretKeySpec(keyBytes, "AES");
    }

    private byte[] parseKey(String keyStr) {
        // Try Base64
        try {
            byte[] decoded = Base64.getDecoder().decode(keyStr);
            if (decoded.length == 32 || decoded.length == 16) {
                return decoded;
            }
        } catch (IllegalArgumentException ignored) {
        }

        // Try Hex
        try {
            if (keyStr.length() == 64) {
                return HexFormat.of().parseHex(keyStr);
            }
        } catch (Exception ignored) {
        }

        // Use UTF-8 bytes padded or hashed to 256 bits
        byte[] rawBytes = keyStr.getBytes(StandardCharsets.UTF_8);
        if (rawBytes.length == 32) {
            return rawBytes;
        }

        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            return sha256.digest(rawBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm missing", e);
        }
    }

    public String encrypt(String plainSecret) {
        if (plainSecret == null || plainSecret.isBlank()) {
            return plainSecret;
        }

        if (plainSecret.startsWith(ENCRYPTED_PREFIX)) {
            return plainSecret; // Already encrypted
        }

        try {
            byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
            SECURE_RANDOM.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec);

            byte[] cipherText = cipher.doFinal(plainSecret.getBytes(StandardCharsets.UTF_8));

            String ivBase64 = Base64.getUrlEncoder().withoutPadding().encodeToString(iv);
            String cipherBase64 = Base64.getUrlEncoder().withoutPadding().encodeToString(cipherText);

            return ENCRYPTED_PREFIX + ivBase64 + ":" + cipherBase64;
        } catch (Exception e) {
            log.error("Failed to encrypt webhook signing secret at rest");
            throw new IllegalStateException("Encryption error occurred while protecting webhook secret", e);
        }
    }

    public String decrypt(String storedSecret) {
        if (storedSecret == null || storedSecret.isBlank()) {
            return storedSecret;
        }

        if (!storedSecret.startsWith(ENCRYPTED_PREFIX)) {
            // Backward-compatible fallback for existing unencrypted secrets
            return storedSecret;
        }

        try {
            String content = storedSecret.substring(ENCRYPTED_PREFIX.length());
            int colonIndex = content.indexOf(':');
            if (colonIndex == -1) {
                throw new IllegalArgumentException("Malformed encrypted webhook secret format");
            }

            String ivBase64 = content.substring(0, colonIndex);
            String cipherBase64 = content.substring(colonIndex + 1);

            byte[] iv = Base64.getUrlDecoder().decode(ivBase64);
            byte[] cipherText = Base64.getUrlDecoder().decode(cipherBase64);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec);

            byte[] plainBytes = cipher.doFinal(cipherText);
            return new String(plainBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("Failed to decrypt webhook signing secret");
            throw new IllegalStateException("Decryption error occurred while retrieving webhook secret", e);
        }
    }

    public boolean isEncrypted(String storedSecret) {
        return storedSecret != null && storedSecret.startsWith(ENCRYPTED_PREFIX);
    }

    public String maskSecret(String secret) {
        if (secret == null || secret.isBlank()) {
            return "whsec_***";
        }

        // If an encrypted ciphertext was passed directly, decrypt it first so we can mask correctly
        String plain = decrypt(secret);
        if (plain == null || plain.length() < 10) {
            return "whsec_***";
        }
        return "whsec_************************" + plain.substring(plain.length() - 4);
    }
}
