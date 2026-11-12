package com.swiftdrop.merchant.security;
import org.junit.jupiter.api.Test;
import java.util.Base64;
import static org.junit.jupiter.api.Assertions.*;
class ApiKeyGeneratorTest {
    @Test void randomSecretsHave256BitsAndDistinctIdentifiers() {
        var generator=new ApiKeyGenerator(); var first=generator.generate(); var second=generator.generate();
        assertEquals(32,Base64.getUrlDecoder().decode(first.plaintext().split("\\.")[1]).length);
        assertEquals(first.publicId(),new ApiKeyHasher().publicId(first.plaintext()));
        assertNotEquals(first.publicId(),second.publicId()); assertNotEquals(first.plaintext(),second.plaintext());
        assertFalse(first.toString().contains(first.plaintext()));
    }
}
