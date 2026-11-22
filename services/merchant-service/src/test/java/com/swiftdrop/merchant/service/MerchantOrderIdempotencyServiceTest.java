package com.swiftdrop.merchant.service;
import com.swiftdrop.merchant.entity.*;
import com.swiftdrop.merchant.exception.MerchantException;
import com.swiftdrop.merchant.repository.MerchantOrderRequestRepository;
import com.swiftdrop.merchant.security.VerifiedMerchantApiCredential;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class MerchantOrderIdempotencyServiceTest {
    final Instant now=Instant.parse("2026-11-13T00:00:00Z");
    MerchantOrderRequestRepository repo; MerchantOrderIdempotencyService service; VerifiedMerchantApiCredential actor; MerchantOrderRequest record;
    @BeforeEach void setup() {
        repo=mock(MerchantOrderRequestRepository.class); service=new MerchantOrderIdempotencyService(repo,Clock.fixed(now,ZoneOffset.UTC));
        actor=new VerifiedMerchantApiCredential(UUID.randomUUID(),UUID.randomUUID(),Set.of("orders:write"));
        record=new MerchantOrderRequest(actor.merchantId(),actor.credentialId(),"digest","fingerprint",now);
    }
    void existing() { when(repo.lockRequest(actor.merchantId(),"digest")).thenReturn(Optional.of(record)); }
    @Test void newClaimHasStableDownstreamIdAndLease() { var result=service.claim(actor,"digest","fingerprint"); assertFalse(result.replay()); assertNotNull(result.request().downstreamRequestId()); assertEquals(now.plusSeconds(60),result.request().leaseUntil()); verify(repo).saveAndFlush(result.request()); }
    @Test void fingerprintMismatchConflicts() { existing(); assertEquals("IDEMPOTENCY_CONFLICT",assertThrows(MerchantException.class,()->service.claim(actor,"digest","other")).getCode()); }
    @Test void concurrentClaimDoesNotResend() { existing(); assertEquals("REQUEST_IN_PROGRESS",assertThrows(MerchantException.class,()->service.claim(actor,"digest","fingerprint")).getCode()); }
    @Test void expiredLeaseBecomesUncertainWithoutNewId() {
        existing(); UUID downstream=record.downstreamRequestId(); service=new MerchantOrderIdempotencyService(repo,Clock.fixed(now.plusSeconds(61),ZoneOffset.UTC));
        assertTrue(service.claim(actor,"digest","fingerprint").replay()); assertEquals(MerchantOrderRequestState.UNCERTAIN,record.state()); assertEquals(downstream,record.downstreamRequestId());
    }
    @Test void completedRequestReplaysAfterServiceRecreationAndCredentialRotation() {
        existing(); record.complete(record.attemptToken(),201,"snapshot",UUID.randomUUID(),"location",null,now);
        var rotated=new VerifiedMerchantApiCredential(actor.merchantId(),UUID.randomUUID(),Set.of("orders:write"));
        assertTrue(new MerchantOrderIdempotencyService(repo,Clock.systemUTC()).claim(rotated,"digest","fingerprint").replay());
    }
    @Test void staleCompletionCannotOverwriteOutcome() {
        when(repo.lockById(record.id())).thenReturn(Optional.of(record));
        assertThrows(MerchantException.class,()->service.complete(record.id(),UUID.randomUUID(),201,"json",UUID.randomUUID(),"location",null));
        assertEquals(MerchantOrderRequestState.IN_PROGRESS,record.state()); verify(repo,never()).saveAndFlush(any());
    }
    @Test void uncertainCompletionCannotBeOverwritten() {
        when(repo.lockById(record.id())).thenReturn(Optional.of(record)); service.uncertain(record.id(),record.attemptToken());
        assertThrows(MerchantException.class,()->service.complete(record.id(),record.attemptToken(),201,"json",UUID.randomUUID(),"location",null));
    }
    @Test void uniqueInsertRacePropagatesWithoutDownstreamCall() { when(repo.saveAndFlush(any())).thenThrow(new org.springframework.dao.DataIntegrityViolationException("race")); assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->service.claim(actor,"digest","fingerprint")); }
}
