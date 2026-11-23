package com.swiftdrop.merchant.repository;

import com.swiftdrop.merchant.entity.MerchantBankAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MerchantBankAccountRepository extends JpaRepository<MerchantBankAccount, UUID> {
    List<MerchantBankAccount> findByMerchantId(UUID merchantId);
}
