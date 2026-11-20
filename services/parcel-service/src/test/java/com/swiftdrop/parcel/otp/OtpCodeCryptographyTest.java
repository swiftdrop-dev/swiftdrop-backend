package com.swiftdrop.parcel.otp;

import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OtpCodeCryptographyTest {
    private static final OtpHmacKey TEST_KEY = OtpHmacKey.fromBase64(
            Base64.getEncoder().encodeToString(new byte[32]));

    @Test
    void generatesExactlySixDigitsAndPreservesLeadingZeroes() {
        SecureRandom deterministic = new SecureRandom() {
            @Override
            public int nextInt(int bound) {
                assertEquals(1_000_000, bound);
                return 42;
            }
        };
        OtpCodeCryptography cryptography = new OtpCodeCryptography(TEST_KEY, deterministic);

        assertArrayEquals("000042".toCharArray(), cryptography.generateCode());
    }

    @Test
    void hmacIsBoundToChallengeParcelAssignmentAndCourier() {
        OtpCodeCryptography cryptography = new OtpCodeCryptography(TEST_KEY);
        UUID id = UUID.randomUUID();
        char[] code = "004271".toCharArray();
        byte[] digest = cryptography.hash(id, "prc_001", 5L, "cou_001", code);

        assertEquals(32, digest.length);
        assertTrue(cryptography.matches(digest, id, "prc_001", 5L, "cou_001", code));
        assertFalse(cryptography.matches(digest, id, "prc_002", 5L, "cou_001", code));
        assertFalse(cryptography.matches(digest, id, "prc_001", 6L, "cou_001", code));
        assertFalse(cryptography.matches(digest, id, "prc_001", 5L, "cou_002", code));
        assertFalse(cryptography.matches(digest, UUID.randomUUID(), "prc_001", 5L, "cou_001", code));
        assertFalse(cryptography.matches(digest, id, "prc_001", 5L, "cou_001", "4271".toCharArray()));
    }

    @Test
    void hmacConfigurationRequiresAtLeastThirtyTwoBase64DecodedBytes() {
        assertThrows(IllegalStateException.class, () -> OtpHmacKey.fromBase64(null));
        assertThrows(IllegalStateException.class, () -> OtpHmacKey.fromBase64("not-base64!"));
        assertThrows(IllegalStateException.class, () -> OtpHmacKey.fromBase64(
                Base64.getEncoder().encodeToString(new byte[31])));
        OtpHmacKey.fromBase64(Base64.getEncoder().encodeToString(new byte[32]));
    }

    @Test
    void deliveryCommandAndIssueReceiptDoNotRenderOtpOrHash() {
        char[] code = "001122".toCharArray();
        byte[] digest = new byte[32];
        OtpDeliveryCommand command = new OtpDeliveryCommand(UUID.randomUUID(), "prc_001",
                "+94770000000", code, java.time.Instant.now());
        OtpChallengeReceipt receipt = new OtpChallengeReceipt(UUID.randomUUID(),
                java.time.Instant.now().plusSeconds(300), java.time.Instant.now().plusSeconds(60), "******0000");

        assertFalse(command.toString().contains("001122"));
        assertFalse(receipt.toString().contains("001122"));
        assertFalse(receipt.toString().contains(Base64.getEncoder().encodeToString(digest)));
        assertEquals(4, OtpChallengeReceipt.class.getRecordComponents().length);
    }
}
