package com.swiftdrop.parcel.otp;

import javax.crypto.spec.SecretKeySpec;
import java.util.Arrays;
import java.util.Base64;

public final class OtpHmacKey {
    private final byte[] keyBytes;

    private OtpHmacKey(byte[] keyBytes) {
        this.keyBytes = keyBytes.clone();
    }

    public static OtpHmacKey fromBase64(String configuredSecret) {
        if (configuredSecret == null || configuredSecret.isBlank()) {
            throw new IllegalStateException("PARCEL_OTP_HMAC_SECRET is required");
        }
        final byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(configuredSecret.trim());
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(
                    "PARCEL_OTP_HMAC_SECRET must be Base64-encoded key material", exception);
        }
        if (decoded.length < 32) {
            Arrays.fill(decoded, (byte) 0);
            throw new IllegalStateException("PARCEL_OTP_HMAC_SECRET must contain at least 32 random bytes");
        }
        try {
            return new OtpHmacKey(decoded);
        } finally {
            Arrays.fill(decoded, (byte) 0);
        }
    }

    SecretKeySpec asSecretKeySpec() {
        return new SecretKeySpec(keyBytes.clone(), "HmacSHA256");
    }
}
