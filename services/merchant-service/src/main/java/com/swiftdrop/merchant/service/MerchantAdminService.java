package com.swiftdrop.merchant.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.merchant.dto.request.GenerateApiKeyRequest;
import com.swiftdrop.merchant.dto.response.MerchantAuditLogResponse;
import com.swiftdrop.merchant.dto.response.MerchantDocumentResponse;
import com.swiftdrop.merchant.dto.response.MerchantResponse;
import com.swiftdrop.merchant.dto.response.MerchantSummaryResponse;
import com.swiftdrop.merchant.entity.Merchant;
import com.swiftdrop.merchant.entity.MerchantAuditLog;
import com.swiftdrop.merchant.entity.MerchantDocument;
import com.swiftdrop.merchant.entity.enums.MerchantStatus;
import com.swiftdrop.merchant.entity.enums.PricingTier;
import com.swiftdrop.merchant.entity.enums.VerificationStatus;
import com.swiftdrop.merchant.exception.InvalidStateTransitionException;
import com.swiftdrop.merchant.exception.ResourceNotFoundException;
import com.swiftdrop.merchant.mapper.MerchantMapper;
import com.swiftdrop.merchant.repository.MerchantAuditLogRepository;
import com.swiftdrop.merchant.repository.MerchantDocumentRepository;
import com.swiftdrop.merchant.repository.MerchantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class MerchantAdminService {

    private static final Logger log = LoggerFactory.getLogger(MerchantAdminService.class);

    private final MerchantRepository merchantRepository;
    private final MerchantDocumentRepository documentRepository;
    private final MerchantAuditLogRepository auditLogRepository;
    private final MerchantCredentialService credentialService;
    private final OutboxEventService outboxEventService;
    private final MerchantMapper merchantMapper;
    private final ObjectMapper objectMapper;

    public MerchantAdminService(MerchantRepository merchantRepository,
                                MerchantDocumentRepository documentRepository,
                                MerchantAuditLogRepository auditLogRepository,
                                MerchantCredentialService credentialService,
                                OutboxEventService outboxEventService,
                                MerchantMapper merchantMapper,
                                ObjectMapper objectMapper) {
        this.merchantRepository = merchantRepository;
        this.documentRepository = documentRepository;
        this.auditLogRepository = auditLogRepository;
        this.credentialService = credentialService;
        this.outboxEventService = outboxEventService;
        this.merchantMapper = merchantMapper;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public Page<MerchantSummaryResponse> getPendingMerchants(Pageable pageable) {
        return merchantRepository.findByStatusIn(Arrays.asList(MerchantStatus.SUBMITTED, MerchantStatus.UNDER_REVIEW), pageable)
                .map(merchantMapper::toSummaryResponse);
    }

    @Transactional(readOnly = true)
    public Page<MerchantSummaryResponse> getAllMerchants(MerchantStatus status, Pageable pageable) {
        if (status != null) {
            return merchantRepository.findByStatus(status, pageable).map(merchantMapper::toSummaryResponse);
        }
        return merchantRepository.findAll(pageable).map(merchantMapper::toSummaryResponse);
    }

    @Transactional
    public MerchantResponse approveMerchant(UUID merchantId, PricingTier pricingTier, UUID adminId) {
        Merchant merchant = getMerchant(merchantId);

        if (merchant.getStatus() != MerchantStatus.SUBMITTED && merchant.getStatus() != MerchantStatus.UNDER_REVIEW) {
            throw new InvalidStateTransitionException("Only SUBMITTED or UNDER_REVIEW merchants can be approved. Current: " + merchant.getStatus());
        }

        merchant.setStatus(MerchantStatus.APPROVED);
        merchant.setPricingTier(pricingTier != null ? pricingTier : PricingTier.STANDARD);
        merchant.setApprovedBy(adminId);
        merchant.setApprovedAt(Instant.now());
        merchant.setRejectionReason(null);

        // Record Audit Log
        MerchantAuditLog auditLog = new MerchantAuditLog(
                merchant,
                "MERCHANT_APPROVED",
                adminId,
                "ROLE_ADMIN",
                toJson(Map.of("pricingTier", merchant.getPricingTier(), "approvedAt", merchant.getApprovedAt().toString())),
                null
        );
        merchant.addAuditLog(auditLog);

        Merchant saved = merchantRepository.save(merchant);

        // Generate default API key for approved merchant
        GenerateApiKeyRequest keyRequest = new GenerateApiKeyRequest();
        keyRequest.setLabel("Default Production Key");
        keyRequest.setEnvironment("PRODUCTION");
        keyRequest.setScopes("parcels:create,parcels:read,tracking:read");
        credentialService.generateApiKey(saved.getId(), keyRequest);

        // Publish Outbox Event
        outboxEventService.publishEvent(
                "Merchant",
                saved.getId().toString(),
                "MerchantApprovedEvent",
                toJson(Map.of(
                        "merchantId", saved.getId(),
                        "userId", saved.getUserId(),
                        "businessName", saved.getBusinessName(),
                        "pricingTier", saved.getPricingTier()
                ))
        );

        log.info("Admin {} approved merchant {}", adminId, merchantId);
        return merchantMapper.toMerchantResponse(saved);
    }

    @Transactional
    public MerchantResponse rejectMerchant(UUID merchantId, String rejectionReason, UUID adminId) {
        Merchant merchant = getMerchant(merchantId);

        if (merchant.getStatus() != MerchantStatus.SUBMITTED && merchant.getStatus() != MerchantStatus.UNDER_REVIEW) {
            throw new InvalidStateTransitionException("Only SUBMITTED or UNDER_REVIEW merchants can be rejected. Current: " + merchant.getStatus());
        }

        merchant.setStatus(MerchantStatus.REJECTED);
        merchant.setRejectionReason(rejectionReason);

        MerchantAuditLog auditLog = new MerchantAuditLog(
                merchant,
                "MERCHANT_REJECTED",
                adminId,
                "ROLE_ADMIN",
                toJson(Map.of("rejectionReason", rejectionReason != null ? rejectionReason : "Compliance criteria not met")),
                null
        );
        merchant.addAuditLog(auditLog);

        Merchant saved = merchantRepository.save(merchant);

        outboxEventService.publishEvent(
                "Merchant",
                saved.getId().toString(),
                "MerchantRejectedEvent",
                toJson(Map.of(
                        "merchantId", saved.getId(),
                        "userId", saved.getUserId() != null ? saved.getUserId() : UUID.randomUUID(),
                        "businessName", saved.getBusinessName() != null ? saved.getBusinessName() : "",
                        "rejectionReason", merchant.getRejectionReason() != null ? merchant.getRejectionReason() : ""
                ))
        );

        log.info("Admin {} rejected merchant {}: {}", adminId, merchantId, rejectionReason);
        return merchantMapper.toMerchantResponse(saved);
    }

    @Transactional
    public MerchantDocumentResponse reviewDocument(UUID merchantId, UUID documentId, boolean approve, String rejectionReason, UUID adminId) {
        MerchantDocument document = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found: " + documentId));

        if (!document.getMerchant().getId().equals(merchantId)) {
            throw new ResourceNotFoundException("Document does not belong to merchant: " + merchantId);
        }

        document.setVerificationStatus(approve ? VerificationStatus.VERIFIED : VerificationStatus.REJECTED);
        document.setVerifiedBy(adminId);
        document.setVerifiedAt(Instant.now());
        if (!approve) {
            document.setRejectionReason(rejectionReason);
        } else {
            document.setRejectionReason(null);
        }

        MerchantDocument saved = documentRepository.save(document);

        // Audit log on merchant
        MerchantAuditLog auditLog = new MerchantAuditLog(
                document.getMerchant(),
                approve ? "DOCUMENT_VERIFIED" : "DOCUMENT_REJECTED",
                adminId,
                "ROLE_ADMIN",
                toJson(Map.of("documentId", documentId, "documentType", document.getDocumentType(), "approved", approve)),
                null
        );
        auditLogRepository.save(auditLog);

        log.info("Admin {} reviewed document {} for merchant {}: approved={}", adminId, documentId, merchantId, approve);
        return merchantMapper.toDocumentResponse(saved);
    }

    @Transactional
    public MerchantResponse suspendMerchant(UUID merchantId, String reason, UUID adminId) {
        Merchant merchant = getMerchant(merchantId);

        merchant.setStatus(MerchantStatus.SUSPENDED);

        MerchantAuditLog auditLog = new MerchantAuditLog(
                merchant,
                "MERCHANT_SUSPENDED",
                adminId,
                "ROLE_ADMIN",
                toJson(Map.of("reason", reason != null ? reason : "Administrative suspension")),
                null
        );
        merchant.addAuditLog(auditLog);

        Merchant saved = merchantRepository.save(merchant);

        outboxEventService.publishEvent(
                "Merchant",
                saved.getId().toString(),
                "MerchantSuspendedEvent",
                toJson(Map.of("merchantId", saved.getId(), "reason", reason != null ? reason : ""))
        );

        log.info("Admin {} suspended merchant {}", adminId, merchantId);
        return merchantMapper.toMerchantResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<MerchantAuditLogResponse> getAuditLogs(UUID merchantId) {
        return auditLogRepository.findByMerchantIdOrderByCreatedAtDesc(merchantId).stream()
                .map(merchantMapper::toAuditLogResponse)
                .collect(Collectors.toList());
    }

    private Merchant getMerchant(UUID merchantId) {
        return merchantRepository.findById(merchantId)
                .orElseThrow(() -> new ResourceNotFoundException("Merchant not found: " + merchantId));
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }
}
