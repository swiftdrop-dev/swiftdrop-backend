package com.swiftdrop.merchant.service;
import com.swiftdrop.merchant.entity.*;
import com.swiftdrop.merchant.idempotency.*;
import com.swiftdrop.merchant.repository.MerchantOrderRequestRepository;
import com.swiftdrop.merchant.security.VerifiedMerchantApiCredential;
import com.swiftdrop.merchant.exception.MerchantOrderException;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import org.springframework.transaction.annotation.*;

@Transactional(propagation=Propagation.REQUIRES_NEW)
public class MerchantOrderIdempotencyService {
    private final MerchantOrderRequestRepository repository;
    private final Clock clock;
    public MerchantOrderIdempotencyService(MerchantOrderRequestRepository repository,Clock clock) { this.repository=repository; this.clock=clock; }
    public MerchantOrderClaim claim(VerifiedMerchantApiCredential actor,String digest,String fingerprint) {
        var existing=repository.lockRequest(actor.merchantId(),digest);
        if (existing.isEmpty()) {
            var request=new MerchantOrderRequest(actor.merchantId(),actor.credentialId(),digest,fingerprint,clock.instant());
            repository.saveAndFlush(request); return new MerchantOrderClaim(request,false);
        }
        return resolveExisting(existing.get(), fingerprint);
    }

    public Optional<MerchantOrderClaim> claimExisting(VerifiedMerchantApiCredential actor, String digest, String fingerprint) {
        return repository.lockRequest(actor.merchantId(), digest)
                .map(request -> resolveExisting(request, fingerprint));
    }

    private MerchantOrderClaim resolveExisting(MerchantOrderRequest request, String fingerprint) {
        if (!"v1".equals(request.fingerprintVersion()) || !request.fingerprint().equals(fingerprint)) throw error("IDEMPOTENCY_CONFLICT","Idempotency key was used for a different request");
        if (request.state()==MerchantOrderRequestState.SUCCEEDED || request.state()==MerchantOrderRequestState.REJECTED) return new MerchantOrderClaim(request,true);
        if (request.state()==MerchantOrderRequestState.IN_PROGRESS && clock.instant().isBefore(request.leaseUntil())) throw error("REQUEST_IN_PROGRESS","Request is being processed");
        // Never reclaim or resend: M2 has not guaranteed downstream deduplication.
        request.uncertain(request.attemptToken(),clock.instant()); repository.saveAndFlush(request);
        return new MerchantOrderClaim(request,true);
    }
    public void complete(UUID id,UUID token,int status,String snapshot,String parcelId,String location,String failure) {
        var request=repository.lockById(id).orElseThrow(()->error("CLAIM_LOST","Request claim unavailable"));
        if (!request.attemptToken().equals(token) || request.state()!=MerchantOrderRequestState.IN_PROGRESS) throw error("CLAIM_LOST","Request claim no longer active");
        request.complete(token,status,snapshot,parcelId,location,failure,clock.instant()); repository.saveAndFlush(request);
    }
    public void uncertain(UUID id,UUID token) {
        var request=repository.lockById(id).orElseThrow(()->error("CLAIM_LOST","Request claim unavailable"));
        request.uncertain(token,clock.instant()); repository.saveAndFlush(request);
    }
    private MerchantOrderException error(String code,String message) { return new MerchantOrderException(409,code,message); }
}
