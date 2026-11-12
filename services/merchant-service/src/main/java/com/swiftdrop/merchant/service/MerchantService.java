package com.swiftdrop.merchant.service;
import com.swiftdrop.merchant.dto.request.*;
import com.swiftdrop.merchant.dto.response.MerchantResponse;
import com.swiftdrop.merchant.entity.*;
import com.swiftdrop.merchant.exception.MerchantException;
import com.swiftdrop.merchant.mapper.MerchantMapper;
import com.swiftdrop.merchant.repository.MerchantRepository;
import com.swiftdrop.merchant.security.*;
import jakarta.validation.Validator;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

@Transactional
public class MerchantService {
    /** Proposed M1 authority contract, requiring explicit agreement before integration. */
    public static final String REVIEW_AUTHORITY = "merchant:review";
    private final MerchantRepository repository;
    private final MerchantIdentityResolver identity;
    private final Validator validator;
    public MerchantService(MerchantRepository repository, MerchantIdentityResolver identity, Validator validator) {
        this.repository=repository; this.identity=identity; this.validator=validator;
    }
    public MerchantResponse register(MerchantOnboardingRequest request) {
        MerchantActor actor = actor(); validate(request);
        if (repository.existsByOwnerSubject(actor.subject())) throw error(409, "MERCHANT_EXISTS", "Merchant already registered");
        Merchant merchant = new Merchant(actor.subject(), request.businessName().trim(), request.registrationNumber().trim(),
            request.contactName().trim(), request.contactEmail().trim(), request.contactPhone().trim(),
            request.pickupStreet().trim(), request.pickupCity().trim(), request.pickupDistrict().trim());
        return MerchantMapper.toResponse(repository.saveAndFlush(merchant));
    }
    @Transactional(readOnly=true)
    public MerchantResponse profile() { return MerchantMapper.toResponse(owned(actor())); }
    public MerchantResponse update(MerchantProfileUpdateRequest request) {
        MerchantActor actor = actor(); validate(request);
        Merchant merchant = owned(actor);
        if (merchant.getStatus() != MerchantStatus.APPROVED) throw error(403, "MERCHANT_NOT_APPROVED", "Only approved merchants may update their profile");
        merchant.updateProfile(request.contactName().trim(), request.contactEmail().trim(), request.contactPhone().trim(),
            request.pickupStreet().trim(), request.pickupCity().trim(), request.pickupDistrict().trim());
        return MerchantMapper.toResponse(repository.saveAndFlush(merchant));
    }
    public MerchantResponse review(UUID id, MerchantReviewRequest request) {
        MerchantActor actor = actor();
        if (!actor.authorities().contains(REVIEW_AUTHORITY)) throw error(403, "REVIEW_FORBIDDEN", "Merchant review authority required");
        validate(request);
        Merchant merchant = repository.findById(id).orElseThrow(() -> error(404, "MERCHANT_NOT_FOUND", "Merchant not found"));
        if (merchant.getOwnerSubject().equals(actor.subject())) throw error(403, "SELF_REVIEW_FORBIDDEN", "Cannot review your own application");
        if (merchant.getStatus() != MerchantStatus.PENDING) throw error(409, "INVALID_TRANSITION", "Only pending applications may be reviewed");
        merchant.review(request.decision(), actor.subject(), request.reason() == null ? null : request.reason().trim());
        return MerchantMapper.toResponse(repository.saveAndFlush(merchant));
    }
    private MerchantActor actor() {
        MerchantActor actor = identity.currentActor();
        if (actor == null) throw error(503, "IDENTITY_UNAVAILABLE", "Verified identity required");
        return actor;
    }
    private Merchant owned(MerchantActor actor) {
        return repository.findByOwnerSubject(actor.subject()).orElseThrow(() -> error(404, "MERCHANT_NOT_FOUND", "Merchant not found"));
    }
    private void validate(Object request) {
        if (request == null || !validator.validate(request).isEmpty()) throw error(400, "INVALID_REQUEST", "Request validation failed");
    }
    private static MerchantException error(int status, String code, String message) { return new MerchantException(status, code, message); }
}
