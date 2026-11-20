package com.swiftdrop.merchant.repository;

import com.swiftdrop.merchant.entity.MerchantDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MerchantDocumentRepository extends JpaRepository<MerchantDocument, UUID> {
    List<MerchantDocument> findByMerchantId(UUID merchantId);
}
