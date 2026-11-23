package com.swiftdrop.merchant.repository;

import com.swiftdrop.merchant.entity.WebhookEndpoint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WebhookEndpointRepository extends JpaRepository<WebhookEndpoint, UUID> {

    List<WebhookEndpoint> findByMerchantIdOrderByCreatedAtDesc(UUID merchantId);

    List<WebhookEndpoint> findByMerchantIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID merchantId);

    List<WebhookEndpoint> findByMerchantIdAndIsActiveTrue(UUID merchantId);

    List<WebhookEndpoint> findByMerchantIdAndIsActiveTrueAndDeletedAtIsNull(UUID merchantId);

    Optional<WebhookEndpoint> findByIdAndMerchantId(UUID id, UUID merchantId);

    Optional<WebhookEndpoint> findByIdAndMerchantIdAndDeletedAtIsNull(UUID id, UUID merchantId);
}
