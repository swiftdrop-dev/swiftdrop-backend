package com.swiftdrop.merchant.service;
import com.swiftdrop.merchant.client.*;
import com.swiftdrop.merchant.client.dto.*;
import com.swiftdrop.merchant.dto.request.*;
import com.swiftdrop.merchant.dto.response.*;
import com.swiftdrop.merchant.entity.*;
import com.swiftdrop.merchant.exception.MerchantOrderException;
import com.swiftdrop.merchant.idempotency.*;
import com.swiftdrop.merchant.mapper.MerchantOrderMapper;
import com.swiftdrop.merchant.security.*;
import jakarta.validation.Validator;
import java.util.UUID;

/** Deliberately not transactional: network calls occur between separate coordination transactions. */
public class MerchantOrderService {
    private final ParcelClient client; private final MerchantOrderIdempotencyService idempotency;
    private final MerchantOrderRequestFingerprint fingerprint; private final MerchantOrderReplayCodec replay;
    private final MerchantOrderMapper mapper; private final Validator validator;
    public MerchantOrderService(ParcelClient client,MerchantOrderIdempotencyService idempotency,MerchantOrderRequestFingerprint fingerprint,
        MerchantOrderReplayCodec replay,MerchantOrderMapper mapper,Validator validator) {
        this.client=client; this.idempotency=idempotency; this.fingerprint=fingerprint; this.replay=replay; this.mapper=mapper; this.validator=validator;
    }
    public MerchantOrderResponse create(VerifiedMerchantOrderAccess access,String key,MerchantOrderCreateRequest request) {
        var actor=authorize(access,"orders:write"); validate(request);
        if (key==null || !key.matches("[A-Za-z0-9._:-]{1,128}")) throw error(400,"INVALID_REQUEST","Valid Idempotency-Key required");
        var claim=idempotency.claim(actor,fingerprint.keyDigest(key),fingerprint.fingerprint(request)); var record=claim.request();
        if (claim.replay()) {
            if (record.state()==MerchantOrderRequestState.UNCERTAIN) throw error(503,"OUTCOME_UNCERTAIN","Creation outcome requires reconciliation; do not use a new idempotency key");
            if (record.state()==MerchantOrderRequestState.REJECTED) throw error(record.httpStatus(),record.failureCode(),"Parcel service rejected the request");
            if (record.state()!=MerchantOrderRequestState.SUCCEEDED) throw error(409,"REQUEST_IN_PROGRESS","Request is being processed");
            return replay.decode(record.responseVersion(),record.snapshot());
        }
        MerchantOrderResponse response;
        try {
            response=mapper.order(actor.merchantId(),client.createMerchantParcel(actor.merchantId(),record.downstreamRequestId(),new ParcelCreateRequest(request)));
            if (!request.merchantOrderRef().equals(response.merchantOrderRef())) throw new ParcelClientException(502,false);
        } catch (ParcelClientException e) {
            if (e.definitive()) idempotency.complete(record.id(),record.attemptToken(),e.status(),null,null,null,"PARCEL_REQUEST_REJECTED");
            else idempotency.uncertain(record.id(),record.attemptToken());
            throw error(e.status(),e.definitive() ? "PARCEL_REQUEST_REJECTED" : "PARCEL_SERVICE_UNAVAILABLE","Parcel service request failed; retry only with the same idempotency key");
        } catch (RuntimeException e) {
            idempotency.uncertain(record.id(),record.attemptToken()); throw error(503,"OUTCOME_UNCERTAIN","Creation outcome requires reconciliation");
        }
        // A failed completion leaves IN_PROGRESS; lease expiry becomes UNCERTAIN, never a resend.
        idempotency.complete(record.id(),record.attemptToken(),201,replay.encode(response),response.id(),"/api/v1/merchant/orders/"+response.id(),null);
        return response;
    }
    public MerchantOrderPageResponse list(VerifiedMerchantOrderAccess access,MerchantOrderQuery query) {
        var actor=authorize(access,"orders:read"); validate(query);
        try { return mapper.page(actor.merchantId(),client.listMerchantParcels(actor.merchantId(),query),query); }
        catch (ParcelClientException e) { throw error(e.status(),"PARCEL_SERVICE_UNAVAILABLE","Parcel service request failed"); }
    }
    public MerchantOrderResponse detail(VerifiedMerchantOrderAccess access,UUID id) {
        var actor=authorize(access,"orders:read"); if (id==null) throw error(400,"INVALID_REQUEST","Order identifier required");
        try {
            var response=mapper.order(actor.merchantId(),client.getMerchantParcel(actor.merchantId(),id));
            if (!id.equals(response.id())) throw new ParcelClientException(502,false); return response;
        } catch (ParcelClientException e) { throw error(e.status(),e.status()==404 ? "ORDER_NOT_FOUND" : "PARCEL_SERVICE_UNAVAILABLE",e.status()==404 ? "Order not found" : "Parcel service request failed"); }
    }
    private VerifiedMerchantApiCredential authorize(VerifiedMerchantOrderAccess access,String scope) {
        if (access==null || access.credential()==null || access.credential().merchantId()==null || access.credential().credentialId()==null) throw error(401,"INVALID_API_KEY","API credential is invalid");
        if (!access.credential().scopes().contains(scope)) throw error(403,"INSUFFICIENT_SCOPE","Required credential scope unavailable"); return access.credential();
    }
    private void validate(Object value) { if (value==null || !validator.validate(value).isEmpty()) throw error(400,"INVALID_REQUEST","Request validation failed"); }
    private MerchantOrderException error(int status,String code,String message) { return new MerchantOrderException(status,code,message); }
}
