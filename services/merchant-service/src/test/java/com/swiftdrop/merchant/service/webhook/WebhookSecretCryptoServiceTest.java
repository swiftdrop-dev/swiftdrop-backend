package com.swiftdrop.merchant.service.webhook;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebhookSecretCryptoServiceTest {

    private WebhookSecretCryptoService cryptoService;

    @BeforeEach
    void setUp() {
        // Use a test 256-bit base64 key
        String testKey = Base64.getEncoder().encodeToString("12345678901234567890123456789012".getBytes());
        cryptoService = new WebhookSecretCryptoService(testKey);
    }

    @Test
    void shouldEncryptAndDecryptSecretRoundTrip() {
        String originalSecret = "whsec_9876543210abcdef0123456789abcdef";
        String encrypted = cryptoService.encrypt(originalSecret);

        assertNotNull(encrypted);
        assertTrue(encrypted.startsWith("enc:v1:"));
        assertNotEquals(originalSecret, encrypted);

        String decrypted = cryptoService.decrypt(encrypted);
        assertEquals(originalSecret, decrypted);
    }

    @Test
    void shouldGenerateUniqueCiphertextForSameSecretUsingRandomIv() {
        String secret = "whsec_sample_secret_key_1234567890";
        String enc1 = cryptoService.encrypt(secret);
        String enc2 = cryptoService.encrypt(secret);

        assertNotEquals(enc1, enc2, "Each encryption must use a unique random IV");
        assertEquals(secret, cryptoService.decrypt(enc1));
        assertEquals(secret, cryptoService.decrypt(enc2));
    }

    @Test
    void shouldPreserveLegacyPlaintextSecretForBackwardCompatibility() {
        String legacySecret = "whsec_legacy_plaintext_secret_9999";
        // Legacy secret does not start with enc:v1:
        assertFalse(cryptoService.isEncrypted(legacySecret));

        String decrypted = cryptoService.decrypt(legacySecret);
        assertEquals(legacySecret, decrypted, "Legacy unencrypted secrets must be returned as-is");
    }

    @Test
    void shouldMaskSecretWithoutExposingRawKey() {
        String rawSecret = "whsec_abcdef1234567890abcdef1234";
        String masked = cryptoService.maskSecret(rawSecret);

        assertTrue(masked.startsWith("whsec_****"));
        assertTrue(masked.endsWith("1234"));
        assertFalse(masked.contains("abcdef1234567890"));
    }

    @Test
    void shouldMaskEncryptedSecretCorrectly() {
        String rawSecret = "whsec_abcdef1234567890abcdef1234";
        String encrypted = cryptoService.encrypt(rawSecret);
        String masked = cryptoService.maskSecret(encrypted);

        assertTrue(masked.startsWith("whsec_****"));
        assertTrue(masked.endsWith("1234"));
    }

    @Test
    void shouldThrowExceptionOnTamperedCiphertext() {
        String secret = "whsec_critical_secret";
        String encrypted = cryptoService.encrypt(secret);

        // Tamper with the ciphertext
        String tampered = encrypted.substring(0, encrypted.length() - 5) + "AAAAA";
        assertThrows(IllegalStateException.class, () -> cryptoService.decrypt(tampered));
    }

    @Test
    void shouldFailWhenEncryptionKeyIsMissing() {
        assertThrows(IllegalStateException.class, () -> new WebhookSecretCryptoService(""));
        assertThrows(IllegalStateException.class, () -> new WebhookSecretCryptoService("   "));
    }
}
