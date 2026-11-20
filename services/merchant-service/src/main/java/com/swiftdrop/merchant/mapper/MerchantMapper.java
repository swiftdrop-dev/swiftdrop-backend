package com.swiftdrop.merchant.mapper;

import com.swiftdrop.merchant.dto.response.MerchantAddressResponse;
import com.swiftdrop.merchant.dto.response.MerchantApiCredentialResponse;
import com.swiftdrop.merchant.dto.response.MerchantAuditLogResponse;
import com.swiftdrop.merchant.dto.response.MerchantBankAccountResponse;
import com.swiftdrop.merchant.dto.response.MerchantContactResponse;
import com.swiftdrop.merchant.dto.response.MerchantDocumentResponse;
import com.swiftdrop.merchant.dto.response.MerchantResponse;
import com.swiftdrop.merchant.dto.response.MerchantSummaryResponse;
import com.swiftdrop.merchant.entity.Merchant;
import com.swiftdrop.merchant.entity.MerchantAddress;
import com.swiftdrop.merchant.entity.MerchantApiCredential;
import com.swiftdrop.merchant.entity.MerchantAuditLog;
import com.swiftdrop.merchant.entity.MerchantBankAccount;
import com.swiftdrop.merchant.entity.MerchantContact;
import com.swiftdrop.merchant.entity.MerchantDocument;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class MerchantMapper {

    public MerchantResponse toMerchantResponse(Merchant merchant) {
        if (merchant == null) {
            return null;
        }

        MerchantResponse response = new MerchantResponse();
        response.setId(merchant.getId());
        response.setUserId(merchant.getUserId());
        response.setBusinessName(merchant.getBusinessName());
        response.setTradeName(merchant.getTradeName());
        response.setRegistrationNumber(merchant.getRegistrationNumber());
        response.setTaxId(merchant.getTaxId());
        response.setBusinessType(merchant.getBusinessType());
        response.setStatus(merchant.getStatus());
        response.setPricingTier(merchant.getPricingTier());
        response.setRejectionReason(merchant.getRejectionReason());
        response.setApprovedBy(merchant.getApprovedBy());
        response.setApprovedAt(merchant.getApprovedAt());
        response.setWebhookUrl(merchant.getWebhookUrl());
        response.setCreatedAt(merchant.getCreatedAt());
        response.setUpdatedAt(merchant.getUpdatedAt());

        if (merchant.getContacts() != null) {
            response.setContacts(merchant.getContacts().stream()
                    .map(this::toContactResponse)
                    .collect(Collectors.toList()));
        }

        if (merchant.getAddresses() != null) {
            response.setAddresses(merchant.getAddresses().stream()
                    .map(this::toAddressResponse)
                    .collect(Collectors.toList()));
        }

        if (merchant.getBankAccounts() != null) {
            response.setBankAccounts(merchant.getBankAccounts().stream()
                    .map(this::toBankAccountResponse)
                    .collect(Collectors.toList()));
        }

        if (merchant.getDocuments() != null) {
            response.setDocuments(merchant.getDocuments().stream()
                    .map(this::toDocumentResponse)
                    .collect(Collectors.toList()));
        }

        if (merchant.getApiCredentials() != null) {
            response.setApiCredentials(merchant.getApiCredentials().stream()
                    .map(this::toApiCredentialResponse)
                    .collect(Collectors.toList()));
        }

        return response;
    }

    public MerchantSummaryResponse toSummaryResponse(Merchant merchant) {
        if (merchant == null) {
            return null;
        }

        MerchantSummaryResponse response = new MerchantSummaryResponse();
        response.setId(merchant.getId());
        response.setUserId(merchant.getUserId());
        response.setBusinessName(merchant.getBusinessName());
        response.setTradeName(merchant.getTradeName());
        response.setRegistrationNumber(merchant.getRegistrationNumber());
        response.setBusinessType(merchant.getBusinessType());
        response.setStatus(merchant.getStatus());
        response.setPricingTier(merchant.getPricingTier());
        response.setCreatedAt(merchant.getCreatedAt());

        return response;
    }

    public MerchantContactResponse toContactResponse(MerchantContact contact) {
        if (contact == null) {
            return null;
        }

        MerchantContactResponse response = new MerchantContactResponse();
        response.setId(contact.getId());
        response.setContactType(contact.getContactType());
        response.setName(contact.getName());
        response.setPhone(contact.getPhone());
        response.setEmail(contact.getEmail());
        response.setDesignation(contact.getDesignation());
        return response;
    }

    public MerchantAddressResponse toAddressResponse(MerchantAddress address) {
        if (address == null) {
            return null;
        }

        MerchantAddressResponse response = new MerchantAddressResponse();
        response.setId(address.getId());
        response.setAddressLabel(address.getAddressLabel());
        response.setStreetAddress(address.getStreetAddress());
        response.setCity(address.getCity());
        response.setDistrict(address.getDistrict());
        response.setPostalCode(address.getPostalCode());
        response.setLatitude(address.getLatitude());
        response.setLongitude(address.getLongitude());
        response.setContactPhone(address.getContactPhone());
        response.setIsPrimaryPickup(address.getIsPrimaryPickup());
        response.setIsActive(address.getIsActive());

        return response;
    }

    public MerchantBankAccountResponse toBankAccountResponse(MerchantBankAccount bankAccount) {
        if (bankAccount == null) {
            return null;
        }

        MerchantBankAccountResponse response = new MerchantBankAccountResponse();
        response.setId(bankAccount.getId());
        response.setBankName(bankAccount.getBankName());
        response.setBranchName(bankAccount.getBranchName());
        response.setBranchCode(bankAccount.getBranchCode());
        response.setMaskedAccountNumber(maskAccountNumber(bankAccount.getAccountNumber()));
        response.setAccountHolderName(bankAccount.getAccountHolderName());
        response.setSettlementCycle(bankAccount.getSettlementCycle());
        response.setIsVerified(bankAccount.getIsVerified());
        response.setVerifiedAt(bankAccount.getVerifiedAt());

        return response;
    }

    public MerchantDocumentResponse toDocumentResponse(MerchantDocument document) {
        if (document == null) {
            return null;
        }

        MerchantDocumentResponse response = new MerchantDocumentResponse();
        response.setId(document.getId());
        response.setDocumentType(document.getDocumentType());
        response.setFileName(document.getFileName());
        response.setFileUrl(document.getFileUrl());
        response.setFileSizeBytes(document.getFileSizeBytes());
        response.setMimeType(document.getMimeType());
        response.setVerificationStatus(document.getVerificationStatus());
        response.setRejectionReason(document.getRejectionReason());
        response.setUploadedAt(document.getUploadedAt());
        response.setVerifiedAt(document.getVerifiedAt());

        return response;
    }

    public MerchantApiCredentialResponse toApiCredentialResponse(MerchantApiCredential credential) {
        if (credential == null) {
            return null;
        }

        MerchantApiCredentialResponse response = new MerchantApiCredentialResponse();
        response.setId(credential.getId());
        response.setKeyPrefix(credential.getKeyPrefix());
        response.setEnvironment(credential.getEnvironment());
        response.setLabel(credential.getLabel());
        response.setScopes(credential.getScopes());
        response.setIsActive(credential.getIsActive());
        response.setExpiresAt(credential.getExpiresAt());
        response.setLastUsedAt(credential.getLastUsedAt());
        response.setCreatedAt(credential.getCreatedAt());

        return response;
    }

    public MerchantAuditLogResponse toAuditLogResponse(MerchantAuditLog log) {
        if (log == null) {
            return null;
        }

        MerchantAuditLogResponse response = new MerchantAuditLogResponse();
        response.setId(log.getId());
        response.setAction(log.getAction());
        response.setPerformedBy(log.getPerformedBy());
        response.setPerformedByRole(log.getPerformedByRole());
        response.setDetails(log.getDetails());
        response.setCreatedAt(log.getCreatedAt());

        return response;
    }

    private String maskAccountNumber(String accountNumber) {
        if (accountNumber == null || accountNumber.length() < 4) {
            return "****";
        }
        return "****" + accountNumber.substring(accountNumber.length() - 4);
    }
}
