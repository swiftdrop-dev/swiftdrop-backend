package com.swiftdrop.merchant.repository;

import com.swiftdrop.merchant.entity.MerchantAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MerchantAuditLogRepository extends JpaRepository<MerchantAuditLog, UUID> {
    List<MerchantAuditLog> findByMerchantIdOrderByCreatedAtDesc(UUID merchantId);
}
