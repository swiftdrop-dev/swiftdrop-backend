package com.swiftdrop.merchant.dto.request;

import com.swiftdrop.merchant.entity.enums.SettlementCycle;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class SetBankAccountRequest {

    @NotBlank(message = "Bank name is required")
    @Size(max = 150)
    private String bankName;

    @NotBlank(message = "Branch name is required")
    @Size(max = 150)
    private String branchName;

    @Size(max = 20)
    private String branchCode;

    @NotBlank(message = "Account number is required")
    @Size(max = 50)
    private String accountNumber;

    @NotBlank(message = "Account holder name is required")
    @Size(max = 255)
    private String accountHolderName;

    @NotNull(message = "Settlement cycle is required")
    private SettlementCycle settlementCycle;

    public SetBankAccountRequest() {
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

    public String getAccountNumber() {
        return accountNumber;
    }

    public void setAccountNumber(String accountNumber) {
        this.accountNumber = accountNumber;
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
}
