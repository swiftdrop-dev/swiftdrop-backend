package com.swiftdrop.merchant.security;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ApiKeyHasherTest {
    @Test void verifiesDigestAndRejectsWrongSecretAndVersions() {
        var hasher=new ApiKeyHasher(); String key=new ApiKeyGenerator().generate().plaintext(); String digest=hasher.hash(key);
        assertEquals(64,digest.length()); assertNotEquals(key,digest); assertTrue(hasher.matches(key,digest,ApiKeyHasher.VERSION));
        assertFalse(hasher.matches(new ApiKeyGenerator().generate().plaintext(),digest,ApiKeyHasher.VERSION));
        assertFalse(hasher.matches(key,digest,"unknown")); assertFalse(hasher.matches(key,"invalid",ApiKeyHasher.VERSION));
    }
    @Test void malformedOversizedAndUnsupportedKeysRejected() {
        var h=new ApiKeyHasher();
        for (String value : new String[]{"", "x".repeat(10000), "sdm_v2_bad", "Bearer key"}) assertNull(h.publicId(value));
        assertNull(h.publicId(null));
    }
}
