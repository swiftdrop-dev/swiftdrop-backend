package com.swiftdrop.merchant.repository;
import com.swiftdrop.merchant.entity.MerchantApiCredential;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.*;
import java.util.*;
public interface MerchantApiCredentialRepository extends JpaRepository<MerchantApiCredential, UUID> {
    Optional<MerchantApiCredential> findByIdAndMerchantId(UUID id, UUID merchantId);
    Optional<MerchantApiCredential> findByPublicId(String publicId);
    Page<MerchantApiCredential> findByMerchantId(UUID merchantId, Pageable pageable);
}
