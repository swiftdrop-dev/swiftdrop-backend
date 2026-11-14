package com.swiftdrop.parcel.otp;

import javax.crypto.Mac;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.UUID;

public final class OtpCodeCryptography {
    private static final byte[] DOMAIN = "SwiftDrop:parcel-delivery-otp:v1".getBytes(StandardCharsets.UTF_8);
    private final OtpHmacKey key;
    private final SecureRandom secureRandom;

    public OtpCodeCryptography(OtpHmacKey key) {
        this(key, new SecureRandom());
    }

    OtpCodeCryptography(OtpHmacKey key, SecureRandom secureRandom) {
        this.key = key;
        this.secureRandom = secureRandom;
    }

    public char[] generateCode() {
        int value = secureRandom.nextInt(1_000_000);
        char[] code = new char[6];
        for (int index = code.length - 1; index >= 0; index--) {
            code[index] = (char) ('0' + value % 10);
            value /= 10;
        }
        return code;
    }

    public byte[] hash(UUID challengeId, String parcelId, Long assignmentId,
                       String courierId, char[] code) {
        byte[] codeBytes = new byte[code == null ? 0 : code.length];
        if (code != null) {
            for (int i = 0; i < code.length; i++) {
                codeBytes[i] = (byte) code[i];
            }
        }

        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(key.asSecretKeySpec());
            mac.update(DOMAIN);
            mac.update((byte) 0);
            mac.update(ByteBuffer.allocate(Long.BYTES * 2)
                    .putLong(challengeId.getMostSignificantBits())
                    .putLong(challengeId.getLeastSignificantBits()).array());
            updateString(mac, parcelId);
            mac.update(ByteBuffer.allocate(Long.BYTES).putLong(assignmentId).array());
            updateString(mac, courierId);
            return mac.doFinal(codeBytes);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("HMAC-SHA256 is unavailable", exception);
        } finally {
            Arrays.fill(codeBytes, (byte) 0);
        }
    }

    public boolean matches(byte[] expectedHash, UUID challengeId, String parcelId, Long assignmentId,
                           String courierId, char[] candidateCode) {
        byte[] actualHash = hash(challengeId, parcelId, assignmentId, courierId, candidateCode);
        try {
            boolean hashMatches = MessageDigest.isEqual(expectedHash, actualHash);
            boolean correctFormat = candidateCode != null && candidateCode.length == 6;
            if (correctFormat) {
                for (char digit : candidateCode) {
                    correctFormat &= digit >= '0' && digit <= '9';
                }
            }
            return hashMatches & correctFormat;
        } finally {
            Arrays.fill(actualHash, (byte) 0);
        }
    }

    private void updateString(Mac mac, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        mac.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
        mac.update(bytes);
        Arrays.fill(bytes, (byte) 0);
    }
}
