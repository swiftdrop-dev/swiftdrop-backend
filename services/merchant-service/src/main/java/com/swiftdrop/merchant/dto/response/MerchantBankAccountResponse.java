package com.swiftdrop.merchant.dto.response;

import com.swiftdrop.merchant.entity.enums.SettlementCycle;

import java.time.Instant;
import java.util.UUID;

public class MerchantBankAccountResponse {

    private UUID id;
    private String bankName;
    private String branchName;
    private String branchCode;
    private String maskedAccountNumber;
    private String accountHolderName;
    private SettlementCycle settlementCycle;
    private Boolean isVerified;
    private Instant verifiedAt;

    public MerchantBankAccountResponse() {
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getBankName() {
        return bankName;
    }

    public void setBankName(String bankName) {
        this.bankName = bankName;
    }

    public String getBranchName() {
        return branchName;
    }

    public void setBranchName(String branchName) {
        this.branchName = branchName;
    }

    public String getBranchCode() {
        return branchCode;
    }

    public void setBranchCode(String branchCode) {
        this.branchCode = branchCode;
    }

    public String getMaskedAccountNumber() {
        return maskedAccountNumber;
    }

    public void setMaskedAccountNumber(String maskedAccountNumber) {
        this.maskedAccountNumber = maskedAccountNumber;
    }

    public String getAccountHolderName() {
        return accountHolderName;
    }

    public void setAccountHolderName(String accountHolderName) {
        this.accountHolderName = accountHolderName;
    }

    public SettlementCycle getSettlementCycle() {
        return settlementCycle;
    }

    public void setSettlementCycle(SettlementCycle settlementCycle) {
        this.settlementCycle = settlementCycle;
    }

    public Boolean getIsVerified() {
        return isVerified;
    }

    public void setIsVerified(Boolean isVerified) {
        this.isVerified = isVerified;
    }

    public Instant getVerifiedAt() {
        return verifiedAt;
    }

    public void setVerifiedAt(Instant verifiedAt) {
        this.verifiedAt = verifiedAt;
    }
}
