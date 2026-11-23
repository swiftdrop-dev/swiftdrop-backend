package com.swiftdrop.merchant.service.webhook;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;

@Service
public class WebhookSignatureService {

    private static final Logger log = LoggerFactory.getLogger(WebhookSignatureService.class);
    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final String SECRET_PREFIX = "whsec_";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    public static final long DEFAULT_TOLERANCE_SECONDS = 300L; // 5 minutes

    public String generateSecret() {
        byte[] randomBytes = new byte[24];
        SECURE_RANDOM.nextBytes(randomBytes);
        return SECRET_PREFIX + HexFormat.of().formatHex(randomBytes);
    }

    public String signPayload(String payload, String secret, long timestampSeconds) {
        if (payload == null) {
            payload = "";
        }
        String toSign = timestampSeconds + "." + payload;
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            SecretKeySpec secretKeySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256);
            mac.init(secretKeySpec);
            byte[] signatureBytes = mac.doFinal(toSign.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(signatureBytes);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            log.error("Failed to calculate HMAC-SHA256 signature", e);
            throw new IllegalStateException("Cryptographic signing error", e);
        }
    }

    public String createSignatureHeader(String payload, String secret, long timestampSeconds) {
        String hexSignature = signPayload(payload, secret, timestampSeconds);
        return "t=" + timestampSeconds + ",v1=" + hexSignature;
    }

    public boolean verifySignature(String payload, String signatureHeader, String secret, long toleranceSeconds) {
        if (payload == null || signatureHeader == null || secret == null) {
            return false;
        }

        long timestamp = -1;
        String v1Signature = null;

        String[] parts = signatureHeader.split(",");
        for (String part : parts) {
            String[] kv = part.trim().split("=", 2);
            if (kv.length == 2) {
                if ("t".equalsIgnoreCase(kv[0])) {
                    try {
                        timestamp = Long.parseLong(kv[1]);
                    } catch (NumberFormatException e) {
                        return false;
                    }
                } else if ("v1".equalsIgnoreCase(kv[0])) {
                    v1Signature = kv[1];
                }
            }
        }

        if (timestamp <= 0 || v1Signature == null || v1Signature.isBlank()) {
            return false;
        }

        long nowSeconds = Instant.now().getEpochSecond();
        if (Math.abs(nowSeconds - timestamp) > toleranceSeconds) {
            log.warn("Webhook signature timestamp expired or outside tolerance window: ts={}, now={}", timestamp, nowSeconds);
            return false;
        }

        String expectedSignature = signPayload(payload, secret, timestamp);
        byte[] expectedBytes = expectedSignature.getBytes(StandardCharsets.UTF_8);
        byte[] actualBytes = v1Signature.getBytes(StandardCharsets.UTF_8);

        return MessageDigest.isEqual(expectedBytes, actualBytes);
    }

    public boolean verifySignature(String payload, String signatureHeader, String secret) {
        return verifySignature(payload, signatureHeader, secret, DEFAULT_TOLERANCE_SECONDS);
    }
}
