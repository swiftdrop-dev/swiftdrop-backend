package com.swiftdrop.merchant.service;
import com.swiftdrop.merchant.*;
import com.swiftdrop.merchant.client.*;
import com.swiftdrop.merchant.client.dto.*;
import com.swiftdrop.merchant.dto.request.*;
import com.swiftdrop.merchant.dto.response.MerchantOrderResponse;
import com.swiftdrop.merchant.entity.*;
import com.swiftdrop.merchant.exception.MerchantOrderException;
import com.swiftdrop.merchant.idempotency.*;
import com.swiftdrop.merchant.mapper.MerchantOrderMapper;
import com.swiftdrop.merchant.repository.MerchantOrderRequestRepository;
import com.swiftdrop.merchant.security.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.*;
import org.springframework.dao.DataIntegrityViolationException;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class MerchantOrderServiceTest {
    static ValidatorFactory factory; ParcelClient client; MerchantOrderIdempotencyService idem; MerchantOrderService service;
    VerifiedMerchantOrderAccess actor; MerchantOrderRequest record; ParcelResponse parcel;
    @BeforeAll static void validators() { factory=Validation.buildDefaultValidatorFactory(); }
    @AfterAll static void close() { factory.close(); }
    @BeforeEach void setup() {
        client=mock(ParcelClient.class); idem=mock(MerchantOrderIdempotencyService.class);
        actor=new VerifiedMerchantOrderAccess(new VerifiedMerchantApiCredential(UUID.randomUUID(),UUID.randomUUID(),Set.of("orders:write","orders:read")));
        record=new MerchantOrderRequest(actor.credential().merchantId(),actor.credential().credentialId(),"digest","fingerprint",Instant.now());
        parcel=OrderTestData.response(actor.credential().merchantId(),OrderTestData.PARCEL_ID);
        when(idem.claim(eq(actor.credential()),anyString(),anyString())).thenReturn(new MerchantOrderClaim(record,false));
        when(client.createMerchantParcel(eq(actor.credential().merchantId()),eq(record.downstreamRequestId()),any())).thenReturn(parcel);
        var json=new ObjectMapper().findAndRegisterModules();
        service=new MerchantOrderService(client,idem,new MerchantOrderRequestFingerprint(json),new MerchantOrderReplayCodec(json),new MerchantOrderMapper(factory.getValidator()),factory.getValidator());
    }
    @Test void createUsesVerifiedOwnerAndStableDownstreamId() { assertEquals(parcel.order(),service.create(actor,"key",OrderTestData.request())); verify(idem).complete(eq(record.id()),eq(record.attemptToken()),eq(201),anyString(),eq(parcel.order().id()),eq("/api/v1/merchant/orders/"+parcel.order().id()),isNull()); }
    @Test void completedReplayDoesNotCallParcelService() {
        record.complete(record.attemptToken(),201,new MerchantOrderReplayCodec(new ObjectMapper().findAndRegisterModules()).encode(parcel.order()),parcel.order().id(),"location",null,Instant.now());
        when(idem.claim(eq(actor.credential()),anyString(),anyString())).thenReturn(new MerchantOrderClaim(record,true));
        assertEquals(parcel.order(),service.create(actor,"key",OrderTestData.request())); verifyNoInteractions(client);
    }
    @Test void simultaneousFirstClaimsReturnOneCreateAndOneInProgressThenReplay() throws Exception {
        var repository = mock(MerchantOrderRequestRepository.class);
        var winningRow = new AtomicReference<MerchantOrderRequest>();
        var reads = new AtomicInteger();
        var initialReads = new CyclicBarrier(2);
        var releaseParcel = new CountDownLatch(1);
        var request = OrderTestData.request();
        var json = new ObjectMapper().findAndRegisterModules();
        var fingerprints = new MerchantOrderRequestFingerprint(json);
        var realIdempotency = new MerchantOrderIdempotencyService(repository, Clock.systemUTC());
        var orderService = new MerchantOrderService(client, realIdempotency, fingerprints,
                new MerchantOrderReplayCodec(json), new MerchantOrderMapper(factory.getValidator()), factory.getValidator());

        when(repository.lockRequest(actor.credential().merchantId(), fingerprints.keyDigest("key")))
                .thenAnswer(invocation -> {
                    if (reads.incrementAndGet() <= 2) {
                        initialReads.await(5, TimeUnit.SECONDS);
                        return Optional.empty();
                    }
                    return Optional.ofNullable(winningRow.get());
                });
        when(repository.saveAndFlush(any(MerchantOrderRequest.class))).thenAnswer(invocation -> {
            MerchantOrderRequest candidate = invocation.getArgument(0);
            if (winningRow.compareAndSet(null, candidate) || winningRow.get() == candidate) {
                return candidate;
            }
            throw duplicateClaim();
        });
        when(repository.lockById(any())).thenAnswer(invocation -> Optional.of(winningRow.get()));
        when(client.createMerchantParcel(eq(actor.credential().merchantId()), any(), any()))
                .thenAnswer(invocation -> {
                    assertTrue(releaseParcel.await(10, TimeUnit.SECONDS));
                    return parcel;
                });

        try (var executor = Executors.newFixedThreadPool(2)) {
            var requests = new ExecutorCompletionService<MerchantOrderResponse>(executor);
            requests.submit(() -> orderService.create(actor, "key", request));
            requests.submit(() -> orderService.create(actor, "key", request));
            try {
                var first = requests.poll(10, TimeUnit.SECONDS);
                assertNotNull(first);
                var failure = assertThrows(ExecutionException.class, first::get);
                var conflict = assertInstanceOf(MerchantOrderException.class, failure.getCause());
                assertEquals(409, conflict.getStatus());
                assertEquals("REQUEST_IN_PROGRESS", conflict.getCode());
            } finally {
                releaseParcel.countDown();
            }
            var second = requests.poll(10, TimeUnit.SECONDS);
            assertNotNull(second);
            assertEquals(parcel.order(), second.get());
        }
        assertEquals(parcel.order(), orderService.create(actor, "key", request));
        verify(client).createMerchantParcel(eq(actor.credential().merchantId()),
                eq(winningRow.get().downstreamRequestId()), any());
    }

    @Test void duplicateClaimCanReplayWinnerThatCompletedBeforeReread() {
        record.complete(record.attemptToken(), 201,
                new MerchantOrderReplayCodec(new ObjectMapper().findAndRegisterModules()).encode(parcel.order()),
                parcel.order().id(), "/api/v1/merchant/orders/" + parcel.order().id(), null, Instant.now());
        when(idem.claim(eq(actor.credential()), anyString(), anyString())).thenThrow(duplicateClaim());
        when(idem.claimExisting(eq(actor.credential()), anyString(), anyString()))
                .thenReturn(Optional.of(new MerchantOrderClaim(record, true)));

        assertEquals(parcel.order(), service.create(actor, "key", OrderTestData.request()));
        verifyNoInteractions(client);
    }

    private static DataIntegrityViolationException duplicateClaim() {
        return new DataIntegrityViolationException("duplicate claim", new SQLException("constraint detail", "23505"));
    }
    @Test void uncertainNeverRetries() {
        record.uncertain(record.attemptToken(),Instant.now()); when(idem.claim(eq(actor.credential()),anyString(),anyString())).thenReturn(new MerchantOrderClaim(record,true));
        assertEquals("OUTCOME_UNCERTAIN",assertThrows(MerchantOrderException.class,()->service.create(actor,"key",OrderTestData.request())).getCode()); verifyNoInteractions(client);
    }
    @Test void timeoutRecordsUncertaintyWithoutRetry() {
        when(client.createMerchantParcel(any(),any(),any())).thenThrow(new ParcelClientException(504,false));
        assertEquals(504,assertThrows(MerchantOrderException.class,()->service.create(actor,"key",OrderTestData.request())).getStatus());
        verify(idem).uncertain(record.id(),record.attemptToken()); verify(client,times(1)).createMerchantParcel(any(),any(),any());
    }
    @Test void definitiveRejectionIsPersistedAndReplayed() {
        when(client.createMerchantParcel(any(),any(),any())).thenThrow(new ParcelClientException(422,true));
        assertEquals(422,assertThrows(MerchantOrderException.class,()->service.create(actor,"key",OrderTestData.request())).getStatus());
        verify(idem).complete(record.id(),record.attemptToken(),422,null,null,null,"PARCEL_REQUEST_REJECTED");
        record.complete(record.attemptToken(),422,null,null,null,"PARCEL_REQUEST_REJECTED",Instant.now());
        when(idem.claim(eq(actor.credential()),anyString(),anyString())).thenReturn(new MerchantOrderClaim(record,true)); reset(client);
        assertEquals(422,assertThrows(MerchantOrderException.class,()->service.create(actor,"key",OrderTestData.request())).getStatus()); verifyNoInteractions(client);
    }
    @Test void ownershipMismatchIsNotDisclosed() {
        when(client.getMerchantParcel(any(),any())).thenReturn(OrderTestData.response(UUID.randomUUID(),parcel.order().id()));
        assertEquals(502,assertThrows(MerchantOrderException.class,()->service.detail(actor,parcel.order().id())).getStatus());
    }
    @Test void missingOwnedOrderIs404() { when(client.getMerchantParcel(any(),any())).thenThrow(new ParcelClientException(404,false)); assertEquals(404,assertThrows(MerchantOrderException.class,()->service.detail(actor,OrderTestData.PARCEL_ID)).getStatus()); }
    @Test void invalidParcelIdsNeverReachM2() {
        for (String id : List.of("", "prc with spaces", "../other", "x".repeat(65))) {
            assertEquals(400,assertThrows(MerchantOrderException.class,()->service.detail(actor,id)).getStatus());
        }
        verifyNoInteractions(client);
    }
    @Test void readWriteScopesAreIndependent() {
        var readOnly=new VerifiedMerchantOrderAccess(new VerifiedMerchantApiCredential(actor.credential().merchantId(),actor.credential().credentialId(),Set.of("orders:read")));
        var writeOnly=new VerifiedMerchantOrderAccess(new VerifiedMerchantApiCredential(actor.credential().merchantId(),actor.credential().credentialId(),Set.of("orders:write")));
        assertEquals(403,assertThrows(MerchantOrderException.class,()->service.create(readOnly,"key",OrderTestData.request())).getStatus());
        assertEquals(403,assertThrows(MerchantOrderException.class,()->service.detail(writeOnly,OrderTestData.PARCEL_ID)).getStatus()); verifyNoInteractions(client,idem);
    }
    @Test void invalidPaginationAndFiltersNeverReachM2() {
        for (var query:List.of(new MerchantOrderQuery(-1,20,null,null,null,null,null),new MerchantOrderQuery(0,101,null,null,null,null,null),new MerchantOrderQuery(0,20,"INVALID",null,null,null,null),new MerchantOrderQuery(0,20,null,null,null,Instant.now(),Instant.EPOCH)))
            assertThrows(MerchantOrderException.class,()->service.list(actor,query));
        verifyNoInteractions(client);
    }
    @Test void listDelegatesMerchantFiltersAndPagination() {
        var query=new MerchantOrderQuery(0,20,"PENDING","REF-1",null,null,null);
        when(client.listMerchantParcels(actor.credential().merchantId(),query)).thenReturn(new ParcelPageResponse(List.of(parcel),1,0,20));
        assertEquals(1,service.list(actor,query).total()); verify(client).listMerchantParcels(actor.credential().merchantId(),query);
    }
    @Test void invalidRequestAndIdempotencyKeyDoNotClaim() {
        assertThrows(MerchantOrderException.class,()->service.create(actor," ",OrderTestData.request())); assertThrows(MerchantOrderException.class,()->service.create(actor,"key",null)); verifyNoInteractions(idem,client);
    }
    @Test void failurePersistingOutcomeCannotTriggerResend() {
        doThrow(new IllegalStateException("database unavailable")).when(idem).complete(any(),any(),anyInt(),any(),any(),any(),any());
        assertThrows(IllegalStateException.class,()->service.create(actor,"key",OrderTestData.request())); verify(client,times(1)).createMerchantParcel(any(),any(),any());
    }
}
