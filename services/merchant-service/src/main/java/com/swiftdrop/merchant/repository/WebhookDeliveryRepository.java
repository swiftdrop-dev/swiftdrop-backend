package com.swiftdrop.merchant.repository;

import com.swiftdrop.merchant.entity.WebhookDelivery;
import com.swiftdrop.merchant.entity.enums.WebhookDeliveryStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WebhookDeliveryRepository extends JpaRepository<WebhookDelivery, UUID> {

    Page<WebhookDelivery> findByMerchantIdOrderByCreatedAtDesc(UUID merchantId, Pageable pageable);

    Page<WebhookDelivery> findByMerchantIdAndEventTypeOrderByCreatedAtDesc(UUID merchantId, String eventType, Pageable pageable);

    Page<WebhookDelivery> findByMerchantIdAndStatusOrderByCreatedAtDesc(UUID merchantId, WebhookDeliveryStatus status, Pageable pageable);

    Page<WebhookDelivery> findByMerchantIdAndEventTypeAndStatusOrderByCreatedAtDesc(
            UUID merchantId, String eventType, WebhookDeliveryStatus status, Pageable pageable);

    Optional<WebhookDelivery> findByIdAndMerchantId(UUID id, UUID merchantId);

    List<WebhookDelivery> findByStatusAndNextRetryAtLessThanEqual(WebhookDeliveryStatus status, Instant now);

    @Query("SELECT d.id FROM WebhookDelivery d WHERE d.status = :status AND d.nextRetryAt <= :now ORDER BY d.nextRetryAt ASC")
    List<UUID> findRetryCandidateIds(@Param("status") WebhookDeliveryStatus status, @Param("now") Instant now);

    @Query("SELECT d.id FROM WebhookDelivery d WHERE d.status = :status AND d.claimedAt <= :threshold")
    List<UUID> findStuckDeliveryIds(@Param("status") WebhookDeliveryStatus status, @Param("threshold") Instant threshold);

    @Modifying
    @org.springframework.transaction.annotation.Transactional
    @Query("UPDATE WebhookDelivery d SET d.status = :newStatus, d.claimedAt = :claimedAt WHERE d.id = :id AND d.status IN :eligibleStatuses")
    int claimDelivery(
            @Param("id") UUID id,
            @Param("newStatus") WebhookDeliveryStatus newStatus,
            @Param("claimedAt") Instant claimedAt,
            @Param("eligibleStatuses") Collection<WebhookDeliveryStatus> eligibleStatuses);

    boolean existsByEndpointIdAndEventId(UUID endpointId, String eventId);
}
