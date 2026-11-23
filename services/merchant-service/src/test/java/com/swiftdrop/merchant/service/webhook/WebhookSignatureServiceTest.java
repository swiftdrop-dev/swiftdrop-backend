package com.swiftdrop.merchant.service.webhook;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebhookSignatureServiceTest {

    private final WebhookSignatureService signatureService = new WebhookSignatureService();

    @Test
    void shouldGenerateSecureSecretWithPrefix() {
        String secret1 = signatureService.generateSecret();
        String secret2 = signatureService.generateSecret();

        assertNotNull(secret1);
        assertTrue(secret1.startsWith("whsec_"));
        assertEquals(54, secret1.length()); // whsec_ (6) + 48 hex chars (24 bytes)
        assertNotEquals(secret1, secret2);
    }

    private void assertEquals(int expected, int actual) {
        org.junit.jupiter.api.Assertions.assertEquals(expected, actual);
    }

    @Test
    void shouldCreateAndVerifySignatureHeader() {
        String secret = signatureService.generateSecret();
        String payload = "{\"eventId\":\"evt_123\",\"eventType\":\"PARCEL_DELIVERED\"}";
        long now = Instant.now().getEpochSecond();

        String header = signatureService.createSignatureHeader(payload, secret, now);
        assertNotNull(header);
        assertTrue(header.startsWith("t=" + now + ",v1="));

        boolean valid = signatureService.verifySignature(payload, header, secret);
        assertTrue(valid, "Signature should be valid for unaltered payload and secret");
    }

    @Test
    void shouldFailVerificationWhenPayloadIsTampered() {
        String secret = signatureService.generateSecret();
        String payload = "{\"amount\":100}";
        long now = Instant.now().getEpochSecond();

        String header = signatureService.createSignatureHeader(payload, secret, now);
        boolean valid = signatureService.verifySignature("{\"amount\":200}", header, secret);
        assertFalse(valid, "Tampered payload must fail signature verification");
    }

    @Test
    void shouldFailVerificationWhenSecretIsIncorrect() {
        String secret = signatureService.generateSecret();
        String wrongSecret = signatureService.generateSecret();
        String payload = "{\"status\":\"IN_TRANSIT\"}";
        long now = Instant.now().getEpochSecond();

        String header = signatureService.createSignatureHeader(payload, secret, now);
        boolean valid = signatureService.verifySignature(payload, header, wrongSecret);
        assertFalse(valid, "Incorrect secret must fail signature verification");
    }

    @Test
    void shouldFailVerificationWhenTimestampIsExpiredReplayAttack() {
        String secret = signatureService.generateSecret();
        String payload = "{\"status\":\"DELIVERED\"}";
        long expiredTimestamp = Instant.now().getEpochSecond() - 360L; // 6 minutes ago (tolerance is 300s)

        String header = signatureService.createSignatureHeader(payload, secret, expiredTimestamp);
        boolean valid = signatureService.verifySignature(payload, header, secret, 300L);
        assertFalse(valid, "Replayed message with expired timestamp must fail verification");
    }

    @Test
    void shouldFailVerificationForMalformedHeader() {
        String secret = signatureService.generateSecret();
        assertFalse(signatureService.verifySignature("{}", "malformed", secret));
        assertFalse(signatureService.verifySignature("{}", "t=invalid,v1=1234", secret));
        assertFalse(signatureService.verifySignature("{}", null, secret));
        assertFalse(signatureService.verifySignature(null, "t=100,v1=1234", secret));
    }
}
