package com.swiftdrop.merchant.service;
import com.swiftdrop.merchant.config.*;
import com.swiftdrop.merchant.dto.request.*;
import com.swiftdrop.merchant.entity.*;
import com.swiftdrop.merchant.exception.MerchantException;
import com.swiftdrop.merchant.repository.*;
import com.swiftdrop.merchant.security.*;
import jakarta.validation.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.*;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.*;
import org.springframework.transaction.interceptor.*;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class MerchantApiCredentialServiceTest {
    static ValidatorFactory factory;
    final Instant now=Instant.parse("2026-11-13T00:00:00Z");
    MerchantRepository merchants; MerchantApiCredentialRepository credentials;
    MerchantIdentityResolver identity; MerchantApiCredentialService service; Merchant merchant; MerchantApiCredential key;
    ApiKeyHasher hasher=new ApiKeyHasher();
    @BeforeAll static void validation() { factory=Validation.buildDefaultValidatorFactory(); }
    @AfterAll static void close() { factory.close(); }
    @BeforeEach void setup() {
        merchants=mock(MerchantRepository.class); credentials=mock(MerchantApiCredentialRepository.class); identity=mock(MerchantIdentityResolver.class);
        when(identity.currentActor()).thenReturn(new MerchantActor("owner",Set.of()));
        merchant=new Merchant("owner","Shop","REG","Contact","a@example.com","+94112345678","Street","City","District");
        merchant.review(MerchantStatus.APPROVED,"reviewer",null);
        when(merchants.lockByOwnerSubject("owner")).thenReturn(Optional.of(merchant)); when(merchants.findByOwnerSubject("owner")).thenReturn(Optional.of(merchant));
        var generated=new ApiKeyGenerator().generate();
        key=new MerchantApiCredential(merchant.getId(),generated.publicId(),hasher.hash(generated.plaintext()),ApiKeyHasher.VERSION,"Integration",Set.of("orders:read"),"owner",now.minusSeconds(1),now.plusSeconds(60));
        when(credentials.findByIdAndMerchantId(key.getId(),merchant.getId())).thenReturn(Optional.of(key));
        when(credentials.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
        service=new MerchantApiCredentialService(merchants,credentials,identity,new ApiKeyGenerator(),hasher,new MerchantCredentialPolicy(),Clock.fixed(now,ZoneOffset.UTC),factory.getValidator());
    }
    MerchantApiKeyIssueRequest request() { return new MerchantApiKeyIssueRequest("Integration",Set.of("orders:read"),null); }
    void failure(int status,Runnable action) { assertEquals(status,assertThrows(MerchantException.class,action::run).getStatus()); }
    @Test void issueStoresOnlyHashAndDefaultsTo90Days() {
        var result=service.issue(request()); assertEquals(now.plus(Duration.ofDays(90)),result.credential().expiresAt());
        verify(credentials).saveAndFlush(argThat(c -> hasher.matches(result.apiKey(),c.getSecretDigest(),c.getHashVersion()) && !c.getSecretDigest().equals(result.apiKey())));
        assertFalse(result.toString().contains(result.apiKey()));
    }
    @Test void pendingAndRejectedCannotIssueOrRotate() {
        for (MerchantStatus status : List.of(MerchantStatus.PENDING,MerchantStatus.REJECTED)) {
            var unapproved=new Merchant("owner","Shop","REG","Contact","a@example.com","+94112345678","Street","City","District");
            if (status==MerchantStatus.REJECTED) unapproved.review(status,"reviewer","reason");
            when(merchants.lockByOwnerSubject("owner")).thenReturn(Optional.of(unapproved));
            failure(403,()->service.issue(request())); failure(403,()->service.rotate(key.getId(),new MerchantApiKeyRotationRequest(null)));
        }
        verify(credentials,never()).saveAndFlush(any());
    }
    @Test void unknownScopesAndInvalidExpiryAreRejected() {
        failure(400,()->service.issue(new MerchantApiKeyIssueRequest("Key",Set.of("admin"),null)));
        failure(400,()->service.issue(new MerchantApiKeyIssueRequest("Key",Set.of("orders:read"),now)));
        failure(400,()->service.issue(new MerchantApiKeyIssueRequest("Key",Set.of("orders:read"),now.plus(Duration.ofDays(90)).plusSeconds(1))));
        failure(400,()->service.issue(new MerchantApiKeyIssueRequest(" ",Set.of(),null)));
        verify(credentials,never()).saveAndFlush(any());
    }
    @Test void expirationAtMaximumIsAccepted() { assertEquals(now.plus(Duration.ofDays(90)),service.issue(new MerchantApiKeyIssueRequest("Key",Set.of("orders:write"),now.plus(Duration.ofDays(90)))).credential().expiresAt()); }
    @Test void rotateImmediatelyRevokesOldAndPreservesScopes() {
        var result=service.rotate(key.getId(),new MerchantApiKeyRotationRequest(null));
        assertFalse(key.activeAt(now)); assertEquals(result.credential().id(),key.getReplacementId()); assertEquals(key.getScopes(),result.credential().scopes());
        var order=inOrder(credentials); order.verify(credentials).findByIdAndMerchantId(key.getId(),merchant.getId());
        order.verify(credentials).saveAndFlush(argThat(k -> k!=key)); order.verify(credentials).saveAndFlush(key);
    }
    @Test void failedReplacementLeavesOldKeyUnchanged() {
        when(credentials.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("not disclosed"));
        assertThrows(DataIntegrityViolationException.class,()->service.rotate(key.getId(),new MerchantApiKeyRotationRequest(null)));
        assertTrue(key.activeAt(now)); assertNull(key.getReplacementId());
    }
    @Test void failureAfterRevocationRollsBackTransaction() {
        when(credentials.saveAndFlush(key)).thenThrow(new DataIntegrityViolationException("not disclosed"));
        var manager=mock(PlatformTransactionManager.class); var status=mock(TransactionStatus.class); when(manager.getTransaction(any())).thenReturn(status);
        var proxyFactory=new ProxyFactory(service);
        proxyFactory.addAdvice(new TransactionInterceptor(manager,new AnnotationTransactionAttributeSource()));
        var transactional=(MerchantApiCredentialService)proxyFactory.getProxy();
        assertThrows(DataIntegrityViolationException.class,()->transactional.rotate(key.getId(),new MerchantApiKeyRotationRequest(null)));
        verify(manager).rollback(status); verify(manager,never()).commit(any());
    }
    @Test void repeatedRotationIsConflict() { service.rotate(key.getId(),new MerchantApiKeyRotationRequest(null)); failure(409,()->service.rotate(key.getId(),new MerchantApiKeyRotationRequest(null))); }
    @Test void expiredKeyCannotRotate() {
        var expired=new MerchantApiCredential(merchant.getId(),"id","digest",ApiKeyHasher.VERSION,"Old",Set.of("orders:read"),"owner",now.minusSeconds(60),now);
        when(credentials.findByIdAndMerchantId(expired.getId(),merchant.getId())).thenReturn(Optional.of(expired));
        failure(409,()->service.rotate(expired.getId(),new MerchantApiKeyRotationRequest(null)));
    }
    @Test void revokeIsIdempotent() { service.revoke(key.getId()); service.revoke(key.getId()); verify(credentials,times(1)).saveAndFlush(key); assertEquals(now,key.getRevokedAt()); }
    @Test void unapprovedOwnerCanListAndRevoke() {
        var pending=new Merchant("owner","Shop","REG","Contact","a@example.com","+94112345678","Street","City","District");
        when(merchants.findByOwnerSubject("owner")).thenReturn(Optional.of(pending)); when(merchants.lockByOwnerSubject("owner")).thenReturn(Optional.of(pending));
        when(credentials.findByMerchantId(eq(pending.getId()),any())).thenReturn(Page.empty());
        when(credentials.findByIdAndMerchantId(key.getId(),pending.getId())).thenReturn(Optional.of(key));
        assertEquals(0,service.list(0,20).total()); service.revoke(key.getId()); assertNotNull(key.getRevokedAt());
    }
    @Test void ownershipIsEnforced() { failure(404,()->service.revoke(UUID.randomUUID())); verify(credentials,never()).findById(any()); }
    @Test void paginationIsBounded() { failure(400,()->service.list(-1,20)); failure(400,()->service.list(0,101)); }
    @Test void listUsesOwnerAndStableSorting() {
        when(credentials.findByMerchantId(eq(merchant.getId()),any())).thenReturn(new PageImpl<>(List.of(key)));
        assertEquals(key.getId(),service.list(0,20).data().getFirst().id());
        verify(credentials).findByMerchantId(eq(merchant.getId()),argThat(p -> p.getSort().getOrderFor("id")!=null));
    }
    @Test void identityUnavailableFailsClosed() {
        when(identity.currentActor()).thenThrow(new MerchantException(503,"IDENTITY_UNAVAILABLE","Unavailable"));
        failure(503,()->service.issue(request())); failure(503,()->service.list(0,20));
        failure(503,()->service.rotate(key.getId(),new MerchantApiKeyRotationRequest(null))); failure(503,()->service.revoke(key.getId()));
        verifyNoInteractions(credentials);
    }
    @Test void nullIdentityFailsClosed() { when(identity.currentActor()).thenReturn(null); failure(503,()->service.issue(request())); }
}
