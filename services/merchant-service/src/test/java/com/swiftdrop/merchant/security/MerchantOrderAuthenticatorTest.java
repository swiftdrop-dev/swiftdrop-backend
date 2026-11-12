package com.swiftdrop.merchant.security;
import com.swiftdrop.merchant.exception.MerchantException;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class MerchantOrderAuthenticatorTest {
    @Test void rejectsMissingRepeatedAndBlankHeaders() {
        var verifier=mock(MerchantApiKeyVerifier.class); var auth=new MerchantOrderAuthenticator(verifier);
        assertThrows(MerchantException.class,()->auth.authenticate(null,"orders:read"));
        for (var headers:List.of(List.<String>of(),List.of(" "),List.of("one","two"))) assertThrows(MerchantException.class,()->auth.authenticate(headers,"orders:read"));
        verifyNoInteractions(verifier);
    }
    @Test void delegatesScopeAndIdentityToM4() {
        var verifier=mock(MerchantApiKeyVerifier.class); var actor=new VerifiedMerchantApiCredential(UUID.randomUUID(),UUID.randomUUID(),Set.of("orders:read"));
        when(verifier.verify("key","orders:read")).thenReturn(actor);
        assertSame(actor,new MerchantOrderAuthenticator(verifier).authenticate(List.of("key"),"orders:read").credential());
    }
}
