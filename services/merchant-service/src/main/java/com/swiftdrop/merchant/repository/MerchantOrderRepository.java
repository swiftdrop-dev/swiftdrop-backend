package com.swiftdrop.merchant.repository;

import com.swiftdrop.merchant.entity.MerchantOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MerchantOrderRepository extends JpaRepository<MerchantOrder, UUID> {

    Optional<MerchantOrder> findByIdAndMerchantId(UUID id, UUID merchantId);

    Optional<MerchantOrder> findByMerchantIdAndIdempotencyKey(UUID merchantId, String idempotencyKey);

    Optional<MerchantOrder> findByMerchantIdAndMerchantOrderRef(UUID merchantId, String merchantOrderRef);

    Optional<MerchantOrder> findByMerchantIdAndTrackingNumber(UUID merchantId, String trackingNumber);

    Optional<MerchantOrder> findByMerchantIdAndParcelId(UUID merchantId, String parcelId);

    Page<MerchantOrder> findByMerchantIdOrderByCreatedAtDesc(UUID merchantId, Pageable pageable);

    @Query("SELECT o FROM MerchantOrder o WHERE o.merchant.id = :merchantId " +
           "AND (:status IS NULL OR o.status = :status) " +
           "AND (CAST(:merchantOrderRef AS string) IS NULL OR LOWER(o.merchantOrderRef) LIKE LOWER(CONCAT('%', CAST(:merchantOrderRef AS string), '%'))) " +
           "AND (CAST(:trackingNumber AS string) IS NULL OR LOWER(o.trackingNumber) LIKE LOWER(CONCAT('%', CAST(:trackingNumber AS string), '%'))) " +
           "AND (:createdFrom IS NULL OR o.createdAt >= :createdFrom) " +
           "AND (:createdTo IS NULL OR o.createdAt <= :createdTo) " +
           "ORDER BY o.createdAt DESC")
    Page<MerchantOrder> searchOrders(
            @Param("merchantId") UUID merchantId,
            @Param("status") String status,
            @Param("merchantOrderRef") String merchantOrderRef,
            @Param("trackingNumber") String trackingNumber,
            @Param("createdFrom") Instant createdFrom,
            @Param("createdTo") Instant createdTo,
            Pageable pageable
    );
}
