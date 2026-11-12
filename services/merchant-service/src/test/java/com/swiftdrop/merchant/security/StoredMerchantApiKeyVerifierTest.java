package com.swiftdrop.merchant.security;
import com.swiftdrop.merchant.entity.*;
import com.swiftdrop.merchant.exception.MerchantException;
import com.swiftdrop.merchant.repository.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class StoredMerchantApiKeyVerifierTest {
    final Instant now=Instant.parse("2026-11-13T00:00:00Z");
    MerchantRepository merchants; MerchantApiCredentialRepository credentials; Merchant merchant;
    MerchantApiCredential credential; GeneratedApiKey generated; StoredMerchantApiKeyVerifier verifier;
    @BeforeEach void setup() {
        merchants=mock(MerchantRepository.class); credentials=mock(MerchantApiCredentialRepository.class);
        merchant=new Merchant("owner","Shop","REG","Contact","a@example.com","+94112345678","Street","City","District");
        merchant.review(MerchantStatus.APPROVED,"reviewer",null); generated=new ApiKeyGenerator().generate();
        credential=key(now.plusSeconds(60));
        when(credentials.findByPublicId(generated.publicId())).thenReturn(Optional.of(credential));
        when(merchants.findById(merchant.getId())).thenReturn(Optional.of(merchant));
        verifier=new StoredMerchantApiKeyVerifier(credentials,merchants,new ApiKeyHasher(),Clock.fixed(now,ZoneOffset.UTC));
    }
    MerchantApiCredential key(Instant expiry) { return new MerchantApiCredential(merchant.getId(),generated.publicId(),new ApiKeyHasher().hash(generated.plaintext()),ApiKeyHasher.VERSION,"Key",Set.of("orders:read"),"owner",now.minusSeconds(60),expiry); }
    void denied(int status,String key,String scope) { assertEquals(status,assertThrows(MerchantException.class,()->verifier.verify(key,scope)).getStatus()); }
    @Test void validKeyReturnsOnlyMachineIdentity() { var result=verifier.verify(generated.plaintext(),"orders:read"); assertEquals(merchant.getId(),result.merchantId()); assertEquals(credential.getId(),result.credentialId()); }
    @Test void malformedKeysAvoidLookup() { denied(401,"x".repeat(10000),"orders:read"); denied(401,null,"orders:read"); verifyNoInteractions(credentials); }
    @Test void missingKeyIsGeneric401() { denied(401,new ApiKeyGenerator().generate().plaintext(),"orders:read"); }
    @Test void wrongSecretIsGeneric401() {
        String forged="sdm_v1_"+generated.publicId()+"."+new ApiKeyGenerator().generate().plaintext().split("\\.")[1];
        denied(401,forged,"orders:read"); verifyNoInteractions(merchants);
    }
    @Test void revokedRejected() { credential.revoke(now,"owner","OWNER_REVOKED",null); denied(401,generated.plaintext(),"orders:read"); }
    @Test void expirationBoundaryAndPastRejected() { for (Instant expiry:List.of(now,now.minusSeconds(1))) { when(credentials.findByPublicId(generated.publicId())).thenReturn(Optional.of(key(expiry))); denied(401,generated.plaintext(),"orders:read"); } }
    @Test void pendingAndRejectedMerchantDenied() {
        for (MerchantStatus state:List.of(MerchantStatus.PENDING,MerchantStatus.REJECTED)) {
            var other=new Merchant("owner","Shop","REG","Contact","a@example.com","+94112345678","Street","City","District");
            if (state==MerchantStatus.REJECTED) other.review(state,"reviewer","reason");
            when(merchants.findById(merchant.getId())).thenReturn(Optional.of(other)); denied(401,generated.plaintext(),"orders:read");
        }
    }
    @Test void absentMerchantDenied() { when(merchants.findById(merchant.getId())).thenReturn(Optional.empty()); denied(401,generated.plaintext(),"orders:read"); }
    @Test void missingOrUnsupportedScopeDenied() { denied(403,generated.plaintext(),"orders:write"); denied(403,generated.plaintext(),"admin"); denied(403,generated.plaintext(),null); }
}
