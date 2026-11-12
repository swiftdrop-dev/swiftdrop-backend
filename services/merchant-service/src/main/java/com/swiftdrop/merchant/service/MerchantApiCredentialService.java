package com.swiftdrop.merchant.service;
import com.swiftdrop.merchant.config.MerchantCredentialPolicy;
import com.swiftdrop.merchant.dto.request.*;
import com.swiftdrop.merchant.dto.response.*;
import com.swiftdrop.merchant.entity.*;
import com.swiftdrop.merchant.exception.MerchantException;
import com.swiftdrop.merchant.repository.*;
import com.swiftdrop.merchant.security.*;
import jakarta.validation.Validator;
import java.time.*;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.transaction.annotation.Transactional;

@Transactional
public class MerchantApiCredentialService {
    private final MerchantRepository merchants;
    private final MerchantApiCredentialRepository credentials;
    private final MerchantIdentityResolver identity;
    private final ApiKeyGenerator generator;
    private final ApiKeyHasher hasher;
    private final MerchantCredentialPolicy policy;
    private final Clock clock;
    private final Validator validator;
    public MerchantApiCredentialService(MerchantRepository merchants, MerchantApiCredentialRepository credentials,
        MerchantIdentityResolver identity, ApiKeyGenerator generator, ApiKeyHasher hasher,
        MerchantCredentialPolicy policy, Clock clock, Validator validator) {
        this.merchants=merchants; this.credentials=credentials; this.identity=identity;
        this.generator=generator; this.hasher=hasher; this.policy=policy; this.clock=clock; this.validator=validator;
    }
    public MerchantApiKeyIssuedResponse issue(MerchantApiKeyIssueRequest request) {
        MerchantActor actor=actor(); validate(request);
        Merchant merchant=owned(actor,true); approved(merchant); policy.scopes(request.scopes());
        Instant now=clock.instant(); Instant expires=policy.expiration(request.expiresAt(),now);
        GeneratedApiKey generated=generator.generate();
        MerchantApiCredential key=create(merchant,actor,generated,request.name().trim(),request.scopes(),now,expires);
        credentials.saveAndFlush(key);
        return new MerchantApiKeyIssuedResponse(MerchantApiKeyMetadataResponse.from(key,now),generated.plaintext());
    }
    @Transactional(readOnly=true)
    public MerchantApiKeyPageResponse list(int page, int size) {
        Merchant merchant=owned(actor(),false);
        if (page<0 || size<1 || size>100) throw error(400,"INVALID_REQUEST","Invalid pagination");
        var found=credentials.findByMerchantId(merchant.getId(),PageRequest.of(page,size,Sort.by("createdAt","id").descending()));
        Instant now=clock.instant();
        return new MerchantApiKeyPageResponse(found.getContent().stream().map(k -> MerchantApiKeyMetadataResponse.from(k,now)).toList(),found.getTotalElements(),page,size);
    }
    public MerchantApiKeyIssuedResponse rotate(UUID id, MerchantApiKeyRotationRequest request) {
        MerchantActor actor=actor(); validate(request);
        Merchant merchant=owned(actor,true); approved(merchant);
        MerchantApiCredential old=credential(id,merchant); Instant now=clock.instant();
        if (!old.activeAt(now)) throw error(409,"CREDENTIAL_INACTIVE","Only active credentials may be rotated");
        policy.scopes(old.getScopes()); Instant expires=policy.expiration(request.expiresAt(),now);
        GeneratedApiKey generated=generator.generate();
        MerchantApiCredential replacement=create(merchant,actor,generated,old.getName(),old.getScopes(),now,expires);
        // Flush replacement first. Any subsequent failure rolls the entire transaction back.
        credentials.saveAndFlush(replacement);
        old.revoke(now,actor.subject(),"ROTATED",replacement.getId());
        credentials.saveAndFlush(old);
        return new MerchantApiKeyIssuedResponse(MerchantApiKeyMetadataResponse.from(replacement,now),generated.plaintext());
    }
    public void revoke(UUID id) {
        MerchantActor actor=actor(); Merchant merchant=owned(actor,true);
        MerchantApiCredential key=credential(id,merchant);
        if (key.getRevokedAt()!=null) return;
        key.revoke(clock.instant(),actor.subject(),"OWNER_REVOKED",null); credentials.saveAndFlush(key);
    }
    private MerchantApiCredential create(Merchant merchant, MerchantActor actor, GeneratedApiKey generated,
        String name, Set<String> scopes, Instant now, Instant expires) {
        return new MerchantApiCredential(merchant.getId(),generated.publicId(),hasher.hash(generated.plaintext()),
            ApiKeyHasher.VERSION,name,scopes,actor.subject(),now,expires);
    }
    private MerchantActor actor() {
        MerchantActor actor=identity.currentActor();
        if (actor==null) throw error(503,"IDENTITY_UNAVAILABLE","Verified owner identity required");
        return actor;
    }
    private Merchant owned(MerchantActor actor, boolean lock) {
        return (lock ? merchants.lockByOwnerSubject(actor.subject()) : merchants.findByOwnerSubject(actor.subject()))
            .orElseThrow(() -> error(404,"MERCHANT_NOT_FOUND","Merchant not found"));
    }
    private MerchantApiCredential credential(UUID id, Merchant merchant) {
        if (id==null) throw error(400,"INVALID_REQUEST","Credential identifier required");
        return credentials.findByIdAndMerchantId(id,merchant.getId())
            .orElseThrow(() -> error(404,"CREDENTIAL_NOT_FOUND","Credential not found"));
    }
    private void approved(Merchant merchant) {
        if (merchant.getStatus()!=MerchantStatus.APPROVED) throw error(403,"MERCHANT_NOT_APPROVED","Approved merchant required");
    }
    private void validate(Object request) {
        if (request==null || !validator.validate(request).isEmpty()) throw error(400,"INVALID_REQUEST","Request validation failed");
    }
    private static MerchantException error(int status,String code,String message) { return new MerchantException(status,code,message); }
}
