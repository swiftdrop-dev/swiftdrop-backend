package com.swiftdrop.merchant.repository;

import com.swiftdrop.merchant.entity.MerchantApiCredential;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MerchantApiCredentialRepository extends JpaRepository<MerchantApiCredential, UUID> {

    List<MerchantApiCredential> findByMerchantId(UUID merchantId);

    List<MerchantApiCredential> findByMerchantIdAndIsActiveTrue(UUID merchantId);

    Optional<MerchantApiCredential> findByKeyPrefixAndIsActiveTrue(String keyPrefix);

    Optional<MerchantApiCredential> findByKeyHash(String keyHash);
}
