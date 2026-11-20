package com.swiftdrop.merchant.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.merchant.dto.request.AddMerchantAddressRequest;
import com.swiftdrop.merchant.dto.request.RegisterMerchantRequest;
import com.swiftdrop.merchant.dto.request.SetBankAccountRequest;
import com.swiftdrop.merchant.dto.request.UpdateMerchantProfileRequest;
import com.swiftdrop.merchant.dto.request.UpdateWebhookRequest;
import com.swiftdrop.merchant.dto.request.UploadDocumentRequest;
import com.swiftdrop.merchant.dto.response.MerchantAddressResponse;
import com.swiftdrop.merchant.dto.response.MerchantBankAccountResponse;
import com.swiftdrop.merchant.dto.response.MerchantDocumentResponse;
import com.swiftdrop.merchant.dto.response.MerchantResponse;
import com.swiftdrop.merchant.entity.Merchant;
import com.swiftdrop.merchant.entity.MerchantAddress;
import com.swiftdrop.merchant.entity.MerchantAuditLog;
import com.swiftdrop.merchant.entity.MerchantBankAccount;
import com.swiftdrop.merchant.entity.MerchantContact;
import com.swiftdrop.merchant.entity.MerchantDocument;
import com.swiftdrop.merchant.entity.enums.ContactType;
import com.swiftdrop.merchant.entity.enums.MerchantStatus;
import com.swiftdrop.merchant.exception.DuplicateResourceException;
import com.swiftdrop.merchant.exception.InvalidStateTransitionException;
import com.swiftdrop.merchant.exception.ResourceNotFoundException;
import com.swiftdrop.merchant.mapper.MerchantMapper;
import com.swiftdrop.merchant.repository.MerchantAddressRepository;
import com.swiftdrop.merchant.repository.MerchantBankAccountRepository;
import com.swiftdrop.merchant.repository.MerchantContactRepository;
import com.swiftdrop.merchant.repository.MerchantDocumentRepository;
import com.swiftdrop.merchant.repository.MerchantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class MerchantOnboardingService {

    private static final Logger log = LoggerFactory.getLogger(MerchantOnboardingService.class);

    private final MerchantRepository merchantRepository;
    private final MerchantContactRepository contactRepository;
    private final MerchantAddressRepository addressRepository;
    private final MerchantBankAccountRepository bankAccountRepository;
    private final MerchantDocumentRepository documentRepository;
    private final OutboxEventService outboxEventService;
    private final MerchantMapper merchantMapper;
    private final ObjectMapper objectMapper;

    public MerchantOnboardingService(MerchantRepository merchantRepository,
                                     MerchantContactRepository contactRepository,
                                     MerchantAddressRepository addressRepository,
                                     MerchantBankAccountRepository bankAccountRepository,
                                     MerchantDocumentRepository documentRepository,
                                     OutboxEventService outboxEventService,
                                     MerchantMapper merchantMapper,
                                     ObjectMapper objectMapper) {
        this.merchantRepository = merchantRepository;
        this.contactRepository = contactRepository;
        this.addressRepository = addressRepository;
        this.bankAccountRepository = bankAccountRepository;
        this.documentRepository = documentRepository;
        this.outboxEventService = outboxEventService;
        this.merchantMapper = merchantMapper;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public MerchantResponse registerMerchant(UUID userId, RegisterMerchantRequest request) {
        if (merchantRepository.existsByUserId(userId)) {
            throw new DuplicateResourceException("Merchant already registered for user: " + userId);
        }
        if (merchantRepository.existsByRegistrationNumber(request.getRegistrationNumber())) {
            throw new DuplicateResourceException("Business registration number already registered: " + request.getRegistrationNumber());
        }

        Merchant merchant = new Merchant();
        merchant.setUserId(userId);
        merchant.setBusinessName(request.getBusinessName());
        merchant.setTradeName(request.getTradeName());
        merchant.setRegistrationNumber(request.getRegistrationNumber());
        merchant.setTaxId(request.getTaxId());
        merchant.setBusinessType(request.getBusinessType());
        merchant.setStatus(MerchantStatus.DRAFT);

        MerchantContact contact = new MerchantContact();
        contact.setContactType(ContactType.PRIMARY);
        contact.setName(request.getContactName());
        contact.setPhone(request.getContactPhone());
        contact.setEmail(request.getContactEmail());
        contact.setDesignation("Authorized Representative");
        merchant.addContact(contact);

        MerchantAuditLog auditLog = new MerchantAuditLog(
                merchant,
                "MERCHANT_REGISTERED",
                userId,
                "ROLE_MERCHANT",
                toJson(Map.of("businessName", request.getBusinessName(), "registrationNumber", request.getRegistrationNumber())),
                null
        );
        merchant.addAuditLog(auditLog);

        Merchant saved = merchantRepository.save(merchant);

        outboxEventService.publishEvent(
                "Merchant",
                saved.getId().toString(),
                "MerchantRegisteredEvent",
                toJson(Map.of(
                        "merchantId", saved.getId(),
                        "userId", userId,
                        "businessName", saved.getBusinessName(),
                        "registrationNumber", saved.getRegistrationNumber()
                ))
        );

        log.info("Registered new merchant [id={}, userId={}]", saved.getId(), userId);
        return merchantMapper.toMerchantResponse(saved);
    }

    @Transactional
    public MerchantResponse updateProfile(UUID merchantId, UpdateMerchantProfileRequest request) {
        Merchant merchant = getMerchantEntity(merchantId);

        if (request.getBusinessName() != null && !request.getBusinessName().isBlank()) {
            merchant.setBusinessName(request.getBusinessName());
        }
        if (request.getTradeName() != null) {
            merchant.setTradeName(request.getTradeName());
        }
        if (request.getTaxId() != null) {
            merchant.setTaxId(request.getTaxId());
        }
        if (request.getBusinessType() != null) {
            merchant.setBusinessType(request.getBusinessType());
        }

        Merchant saved = merchantRepository.save(merchant);
        return merchantMapper.toMerchantResponse(saved);
    }

    @Transactional
    public MerchantAddressResponse addAddress(UUID merchantId, AddMerchantAddressRequest request) {
        Merchant merchant = getMerchantEntity(merchantId);

        if (Boolean.TRUE.equals(request.getIsPrimaryPickup())) {
            addressRepository.findByMerchantId(merchantId).forEach(addr -> {
                addr.setIsPrimaryPickup(false);
                addressRepository.save(addr);
            });
        } else {
            // If this is the first address, automatically make it primary
            if (addressRepository.findByMerchantId(merchantId).isEmpty()) {
                request.setIsPrimaryPickup(true);
            }
        }

        MerchantAddress address = new MerchantAddress();
        address.setMerchant(merchant);
        address.setAddressLabel(request.getAddressLabel());
        address.setStreetAddress(request.getStreetAddress());
        address.setCity(request.getCity());
        address.setDistrict(request.getDistrict());
        address.setPostalCode(request.getPostalCode());
        address.setLatitude(request.getLatitude());
        address.setLongitude(request.getLongitude());
        address.setContactPhone(request.getContactPhone());
        address.setIsPrimaryPickup(Boolean.TRUE.equals(request.getIsPrimaryPickup()));
        address.setIsActive(true);

        MerchantAddress saved = addressRepository.save(address);
        log.info("Added address [id={}, label={}] to merchant {}", saved.getId(), saved.getAddressLabel(), merchantId);
        return merchantMapper.toAddressResponse(saved);
    }

    @Transactional
    public MerchantBankAccountResponse setBankAccount(UUID merchantId, SetBankAccountRequest request) {
        Merchant merchant = getMerchantEntity(merchantId);

        // Delete or replace existing bank accounts for simplicity (1:1 active settlement)
        bankAccountRepository.findByMerchantId(merchantId).forEach(bankAccountRepository::delete);

        MerchantBankAccount bankAccount = new MerchantBankAccount();
        bankAccount.setMerchant(merchant);
        bankAccount.setBankName(request.getBankName());
        bankAccount.setBranchName(request.getBranchName());
        bankAccount.setBranchCode(request.getBranchCode());
        bankAccount.setAccountNumber(request.getAccountNumber());
        bankAccount.setAccountHolderName(request.getAccountHolderName());
        bankAccount.setSettlementCycle(request.getSettlementCycle());
        bankAccount.setIsVerified(false);

        MerchantBankAccount saved = bankAccountRepository.save(bankAccount);
        log.info("Set bank account for merchant [id={}, bank={}]", merchantId, saved.getBankName());
        return merchantMapper.toBankAccountResponse(saved);
    }

    @Transactional
    public MerchantDocumentResponse uploadDocument(UUID merchantId, UploadDocumentRequest request) {
        Merchant merchant = getMerchantEntity(merchantId);

        MerchantDocument document = new MerchantDocument();
        document.setMerchant(merchant);
        document.setDocumentType(request.getDocumentType());
        document.setFileName(request.getFileName());
        document.setFileUrl(request.getFileUrl());
        document.setFileSizeBytes(request.getFileSizeBytes());
        document.setMimeType(request.getMimeType());

        MerchantDocument saved = documentRepository.save(document);
        log.info("Uploaded KYC document [id={}, type={}] for merchant {}", saved.getId(), saved.getDocumentType(), merchantId);
        return merchantMapper.toDocumentResponse(saved);
    }

    @Transactional
    public MerchantResponse submitForVerification(UUID merchantId) {
        Merchant merchant = getMerchantEntity(merchantId);

        if (merchant.getStatus() != MerchantStatus.DRAFT && merchant.getStatus() != MerchantStatus.REJECTED) {
            throw new InvalidStateTransitionException("Only DRAFT or REJECTED merchants can submit for verification. Current status: " + merchant.getStatus());
        }

        // Verification validations
        if (merchant.getAddresses().isEmpty()) {
            throw new InvalidStateTransitionException("Merchant must have at least one pickup address before submitting.");
        }
        if (merchant.getBankAccounts().isEmpty()) {
            throw new InvalidStateTransitionException("Merchant must have settlement bank account details before submitting.");
        }
        if (merchant.getDocuments().isEmpty()) {
            throw new InvalidStateTransitionException("Merchant must upload at least one KYC document (e.g. Business Registration) before submitting.");
        }

        merchant.setStatus(MerchantStatus.SUBMITTED);
        merchant.setRejectionReason(null); // Clear previous rejection reason if any

        MerchantAuditLog auditLog = new MerchantAuditLog(
                merchant,
                "SUBMITTED_FOR_VERIFICATION",
                merchant.getUserId(),
                "ROLE_MERCHANT",
                toJson(Map.of("previousStatus", "DRAFT", "newStatus", "SUBMITTED")),
                null
        );
        merchant.addAuditLog(auditLog);

        Merchant saved = merchantRepository.save(merchant);

        outboxEventService.publishEvent(
                "Merchant",
                saved.getId().toString(),
                "MerchantSubmittedEvent",
                toJson(Map.of(
                        "merchantId", saved.getId(),
                        "businessName", saved.getBusinessName(),
                        "userId", saved.getUserId()
                ))
        );

        log.info("Merchant {} submitted for administrative verification", merchantId);
        return merchantMapper.toMerchantResponse(saved);
    }

    @Transactional
    public MerchantResponse updateWebhook(UUID merchantId, UpdateWebhookRequest request) {
        Merchant merchant = getMerchantEntity(merchantId);
        merchant.setWebhookUrl(request.getWebhookUrl());
        if (request.getWebhookSecret() != null) {
            merchant.setWebhookSecret(request.getWebhookSecret());
        }
        Merchant saved = merchantRepository.save(merchant);
        return merchantMapper.toMerchantResponse(saved);
    }

    @Transactional(readOnly = true)
    public MerchantResponse getMerchantById(UUID merchantId) {
        return merchantMapper.toMerchantResponse(getMerchantEntity(merchantId));
    }

    @Transactional(readOnly = true)
    public MerchantResponse getMerchantByUserId(UUID userId) {
        Merchant merchant = merchantRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Merchant not found for user: " + userId));
        return merchantMapper.toMerchantResponse(merchant);
    }

    public Merchant getMerchantEntity(UUID merchantId) {
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
