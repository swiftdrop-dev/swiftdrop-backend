package com.swiftdrop.merchant.dto.response;

import com.swiftdrop.merchant.entity.enums.BusinessType;
import com.swiftdrop.merchant.entity.enums.MerchantStatus;
import com.swiftdrop.merchant.entity.enums.PricingTier;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class MerchantResponse {

    private UUID id;
    private UUID userId;
    private String businessName;
    private String tradeName;
    private String registrationNumber;
    private String taxId;
    private BusinessType businessType;
    private MerchantStatus status;
    private PricingTier pricingTier;
    private String rejectionReason;
    private UUID approvedBy;
    private Instant approvedAt;
    private String webhookUrl;
    private Instant createdAt;
    private Instant updatedAt;

    private List<MerchantContactResponse> contacts = new ArrayList<>();
    private List<MerchantAddressResponse> addresses = new ArrayList<>();
    private List<MerchantBankAccountResponse> bankAccounts = new ArrayList<>();
    private List<MerchantDocumentResponse> documents = new ArrayList<>();
    private List<MerchantApiCredentialResponse> apiCredentials = new ArrayList<>();

    public MerchantResponse() {
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public String getBusinessName() {
        return businessName;
    }

    public void setBusinessName(String businessName) {
        this.businessName = businessName;
    }

    public String getTradeName() {
        return tradeName;
    }

    public void setTradeName(String tradeName) {
        this.tradeName = tradeName;
    }

    public String getRegistrationNumber() {
        return registrationNumber;
    }

    public void setRegistrationNumber(String registrationNumber) {
        this.registrationNumber = registrationNumber;
    }

    public String getTaxId() {
        return taxId;
    }

    public void setTaxId(String taxId) {
        this.taxId = taxId;
    }

    public BusinessType getBusinessType() {
        return businessType;
    }

    public void setBusinessType(BusinessType businessType) {
        this.businessType = businessType;
    }

    public MerchantStatus getStatus() {
        return status;
    }

    public void setStatus(MerchantStatus status) {
        this.status = status;
    }

    public PricingTier getPricingTier() {
        return pricingTier;
    }

    public void setPricingTier(PricingTier pricingTier) {
        this.pricingTier = pricingTier;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public void setRejectionReason(String rejectionReason) {
        this.rejectionReason = rejectionReason;
    }

    public UUID getApprovedBy() {
        return approvedBy;
    }

    public void setApprovedBy(UUID approvedBy) {
        this.approvedBy = approvedBy;
    }

    public Instant getApprovedAt() {
        return approvedAt;
    }

    public void setApprovedAt(Instant approvedAt) {
        this.approvedAt = approvedAt;
    }

    public String getWebhookUrl() {
        return webhookUrl;
    }

    public void setWebhookUrl(String webhookUrl) {
        this.webhookUrl = webhookUrl;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public List<MerchantContactResponse> getContacts() {
        return contacts;
    }

    public void setContacts(List<MerchantContactResponse> contacts) {
        this.contacts = contacts;
    }

    public List<MerchantAddressResponse> getAddresses() {
        return addresses;
    }

    public void setAddresses(List<MerchantAddressResponse> addresses) {
        this.addresses = addresses;
    }

    public List<MerchantBankAccountResponse> getBankAccounts() {
        return bankAccounts;
    }

    public void setBankAccounts(List<MerchantBankAccountResponse> bankAccounts) {
        this.bankAccounts = bankAccounts;
    }

    public List<MerchantDocumentResponse> getDocuments() {
        return documents;
    }

    public void setDocuments(List<MerchantDocumentResponse> documents) {
        this.documents = documents;
    }

    public List<MerchantApiCredentialResponse> getApiCredentials() {
        return apiCredentials;
    }

    public void setApiCredentials(List<MerchantApiCredentialResponse> apiCredentials) {
        this.apiCredentials = apiCredentials;
    }
}
