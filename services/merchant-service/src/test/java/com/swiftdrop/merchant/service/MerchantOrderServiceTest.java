package com.swiftdrop.merchant.service;
import com.swiftdrop.merchant.*;
import com.swiftdrop.merchant.client.*;
import com.swiftdrop.merchant.client.dto.*;
import com.swiftdrop.merchant.dto.request.*;
import com.swiftdrop.merchant.entity.*;
import com.swiftdrop.merchant.exception.MerchantException;
import com.swiftdrop.merchant.idempotency.*;
import com.swiftdrop.merchant.mapper.MerchantOrderMapper;
import com.swiftdrop.merchant.security.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.*;
import java.time.Instant;
import java.util.*;
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
        parcel=OrderTestData.response(actor.credential().merchantId(),UUID.randomUUID());
        when(idem.claim(eq(actor.credential()),anyString(),anyString())).thenReturn(new MerchantOrderClaim(record,false));
        when(client.createMerchantParcel(eq(actor.credential().merchantId()),eq(record.downstreamRequestId()),any())).thenReturn(parcel);
        var json=new ObjectMapper().findAndRegisterModules();
        service=new MerchantOrderService(client,idem,new MerchantOrderRequestFingerprint(json),new MerchantOrderReplayCodec(json),new MerchantOrderMapper(factory.getValidator()),factory.getValidator());
    }
    @Test void createUsesVerifiedOwnerAndStableDownstreamId() { assertEquals(parcel.order(),service.create(actor,"key",OrderTestData.request())); verify(idem).complete(eq(record.id()),eq(record.attemptToken()),eq(201),anyString(),eq(parcel.order().id()),anyString(),isNull()); }
    @Test void completedReplayDoesNotCallParcelService() {
        record.complete(record.attemptToken(),201,new MerchantOrderReplayCodec(new ObjectMapper().findAndRegisterModules()).encode(parcel.order()),parcel.order().id(),"location",null,Instant.now());
        when(idem.claim(eq(actor.credential()),anyString(),anyString())).thenReturn(new MerchantOrderClaim(record,true));
        assertEquals(parcel.order(),service.create(actor,"key",OrderTestData.request())); verifyNoInteractions(client);
    }
    @Test void uncertainNeverRetries() {
        record.uncertain(record.attemptToken(),Instant.now()); when(idem.claim(eq(actor.credential()),anyString(),anyString())).thenReturn(new MerchantOrderClaim(record,true));
        assertEquals("OUTCOME_UNCERTAIN",assertThrows(MerchantException.class,()->service.create(actor,"key",OrderTestData.request())).getCode()); verifyNoInteractions(client);
    }
    @Test void timeoutRecordsUncertaintyWithoutRetry() {
        when(client.createMerchantParcel(any(),any(),any())).thenThrow(new ParcelClientException(504,false));
        assertEquals(504,assertThrows(MerchantException.class,()->service.create(actor,"key",OrderTestData.request())).getStatus());
        verify(idem).uncertain(record.id(),record.attemptToken()); verify(client,times(1)).createMerchantParcel(any(),any(),any());
    }
    @Test void definitiveRejectionIsPersistedAndReplayed() {
        when(client.createMerchantParcel(any(),any(),any())).thenThrow(new ParcelClientException(422,true));
        assertEquals(422,assertThrows(MerchantException.class,()->service.create(actor,"key",OrderTestData.request())).getStatus());
        verify(idem).complete(record.id(),record.attemptToken(),422,null,null,null,"PARCEL_REQUEST_REJECTED");
        record.complete(record.attemptToken(),422,null,null,null,"PARCEL_REQUEST_REJECTED",Instant.now());
        when(idem.claim(eq(actor.credential()),anyString(),anyString())).thenReturn(new MerchantOrderClaim(record,true)); reset(client);
        assertEquals(422,assertThrows(MerchantException.class,()->service.create(actor,"key",OrderTestData.request())).getStatus()); verifyNoInteractions(client);
    }
    @Test void ownershipMismatchIsNotDisclosed() {
        when(client.getMerchantParcel(any(),any())).thenReturn(OrderTestData.response(UUID.randomUUID(),parcel.order().id()));
        assertEquals(502,assertThrows(MerchantException.class,()->service.detail(actor,parcel.order().id())).getStatus());
    }
    @Test void missingOwnedOrderIs404() { when(client.getMerchantParcel(any(),any())).thenThrow(new ParcelClientException(404,false)); assertEquals(404,assertThrows(MerchantException.class,()->service.detail(actor,UUID.randomUUID())).getStatus()); }
    @Test void readWriteScopesAreIndependent() {
        var readOnly=new VerifiedMerchantOrderAccess(new VerifiedMerchantApiCredential(actor.credential().merchantId(),actor.credential().credentialId(),Set.of("orders:read")));
        var writeOnly=new VerifiedMerchantOrderAccess(new VerifiedMerchantApiCredential(actor.credential().merchantId(),actor.credential().credentialId(),Set.of("orders:write")));
        assertEquals(403,assertThrows(MerchantException.class,()->service.create(readOnly,"key",OrderTestData.request())).getStatus());
        assertEquals(403,assertThrows(MerchantException.class,()->service.detail(writeOnly,UUID.randomUUID())).getStatus()); verifyNoInteractions(client,idem);
    }
    @Test void invalidPaginationAndFiltersNeverReachM2() {
        for (var query:List.of(new MerchantOrderQuery(-1,20,null,null,null,null,null),new MerchantOrderQuery(0,101,null,null,null,null,null),new MerchantOrderQuery(0,20,"INVALID",null,null,null,null),new MerchantOrderQuery(0,20,null,null,null,Instant.now(),Instant.EPOCH)))
            assertThrows(MerchantException.class,()->service.list(actor,query));
        verifyNoInteractions(client);
    }
    @Test void listDelegatesMerchantFiltersAndPagination() {
        var query=new MerchantOrderQuery(0,20,"PENDING","REF-1",null,null,null);
        when(client.listMerchantParcels(actor.credential().merchantId(),query)).thenReturn(new ParcelPageResponse(List.of(parcel),1,0,20));
        assertEquals(1,service.list(actor,query).total()); verify(client).listMerchantParcels(actor.credential().merchantId(),query);
    }
    @Test void invalidRequestAndIdempotencyKeyDoNotClaim() {
        assertThrows(MerchantException.class,()->service.create(actor," ",OrderTestData.request())); assertThrows(MerchantException.class,()->service.create(actor,"key",null)); verifyNoInteractions(idem,client);
    }
    @Test void failurePersistingOutcomeCannotTriggerResend() {
        doThrow(new IllegalStateException("database unavailable")).when(idem).complete(any(),any(),anyInt(),any(),any(),any(),any());
        assertThrows(IllegalStateException.class,()->service.create(actor,"key",OrderTestData.request())); verify(client,times(1)).createMerchantParcel(any(),any(),any());
    }
}
