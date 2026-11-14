package com.swiftdrop.merchant.entity;

import com.swiftdrop.merchant.entity.enums.BusinessType;
import com.swiftdrop.merchant.entity.enums.MerchantStatus;
import com.swiftdrop.merchant.entity.enums.PricingTier;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "merchants")
public class Merchant {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;

    @Column(name = "business_name", nullable = false)
    private String businessName;

    @Column(name = "trade_name")
    private String tradeName;

    @Column(name = "registration_number", nullable = false, unique = true, length = 100)
    private String registrationNumber;

    @Column(name = "tax_id", length = 100)
    private String taxId;

    @Enumerated(EnumType.STRING)
    @Column(name = "business_type", nullable = false, length = 50)
    private BusinessType businessType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private MerchantStatus status = MerchantStatus.DRAFT;

    @Enumerated(EnumType.STRING)
    @Column(name = "pricing_tier", nullable = false, length = 50)
    private PricingTier pricingTier = PricingTier.STANDARD;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @Column(name = "approved_by")
    private UUID approvedBy;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "webhook_url", length = 1024)
    private String webhookUrl;

    @Column(name = "webhook_secret", length = 255)
    private String webhookSecret;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    @OneToMany(mappedBy = "merchant", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<MerchantContact> contacts = new ArrayList<>();

    @OneToMany(mappedBy = "merchant", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<MerchantAddress> addresses = new ArrayList<>();

    @OneToMany(mappedBy = "merchant", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<MerchantBankAccount> bankAccounts = new ArrayList<>();

    @OneToMany(mappedBy = "merchant", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<MerchantDocument> documents = new ArrayList<>();

    @OneToMany(mappedBy = "merchant", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<MerchantApiCredential> apiCredentials = new ArrayList<>();

    @OneToMany(mappedBy = "merchant", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<MerchantAuditLog> auditLogs = new ArrayList<>();

    public Merchant() {
    }

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (this.createdAt == null) {
            this.createdAt = now;
        }
        if (this.updatedAt == null) {
            this.updatedAt = now;
        }
        if (this.status == null) {
            this.status = MerchantStatus.DRAFT;
        }
        if (this.pricingTier == null) {
            this.pricingTier = PricingTier.STANDARD;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    // Helper methods for bidirectional relationships
    public void addContact(MerchantContact contact) {
        contacts.add(contact);
        contact.setMerchant(this);
    }

    public void removeContact(MerchantContact contact) {
        contacts.remove(contact);
        contact.setMerchant(null);
    }

    public void addAddress(MerchantAddress address) {
        addresses.add(address);
        address.setMerchant(this);
    }

    public void removeAddress(MerchantAddress address) {
        addresses.remove(address);
        address.setMerchant(null);
    }

    public void addBankAccount(MerchantBankAccount bankAccount) {
        bankAccounts.add(bankAccount);
        bankAccount.setMerchant(this);
    }

    public void removeBankAccount(MerchantBankAccount bankAccount) {
        bankAccounts.remove(bankAccount);
        bankAccount.setMerchant(null);
    }

    public void addDocument(MerchantDocument document) {
        documents.add(document);
        document.setMerchant(this);
    }

    public void addApiCredential(MerchantApiCredential credential) {
        apiCredentials.add(credential);
        credential.setMerchant(this);
    }

    public void addAuditLog(MerchantAuditLog log) {
        auditLogs.add(log);
        log.setMerchant(this);
    }

    // Getters and Setters
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

    public String getWebhookSecret() {
        return webhookSecret;
    }

    public void setWebhookSecret(String webhookSecret) {
        this.webhookSecret = webhookSecret;
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

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public List<MerchantContact> getContacts() {
        return contacts;
    }

    public void setContacts(List<MerchantContact> contacts) {
        this.contacts = contacts;
    }

    public List<MerchantAddress> getAddresses() {
        return addresses;
    }

    public void setAddresses(List<MerchantAddress> addresses) {
        this.addresses = addresses;
    }

    public List<MerchantBankAccount> getBankAccounts() {
        return bankAccounts;
    }

    public void setBankAccounts(List<MerchantBankAccount> bankAccounts) {
        this.bankAccounts = bankAccounts;
    }

    public List<MerchantDocument> getDocuments() {
        return documents;
    }

    public void setDocuments(List<MerchantDocument> documents) {
        this.documents = documents;
    }

    public List<MerchantApiCredential> getApiCredentials() {
        return apiCredentials;
    }

    public void setApiCredentials(List<MerchantApiCredential> apiCredentials) {
        this.apiCredentials = apiCredentials;
    }

    public List<MerchantAuditLog> getAuditLogs() {
        return auditLogs;
    }

    public void setAuditLogs(List<MerchantAuditLog> auditLogs) {
        this.auditLogs = auditLogs;
    }
}
