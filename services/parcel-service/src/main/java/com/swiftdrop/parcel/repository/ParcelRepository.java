package com.swiftdrop.parcel.repository;

import com.swiftdrop.parcel.entity.Parcel;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.Collection;
import java.util.List;

public interface ParcelRepository extends JpaRepository<Parcel, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select parcel from Parcel parcel where parcel.id = :parcelId")
    Optional<Parcel> findByIdForUpdate(@Param("parcelId") String parcelId);

    Page<Parcel> findByCourierIdAndEstimatedDeliveryDateOrderByTrackingNumberAsc(
            String courierId, String estimatedDeliveryDate, Pageable pageable);

    List<Parcel> findByIdInAndCourierId(Collection<String> ids, String courierId);

    Page<Parcel> findBySenderIdOrderByCreatedAtDesc(String senderId, Pageable pageable);

    Page<Parcel> findBySenderIdAndStatusOrderByCreatedAtDesc(String senderId, String status, Pageable pageable);

    Optional<Parcel> findByIdAndSenderId(String id, String senderId);

    Optional<Parcel> findByTrackingNumber(String trackingNumber);

    Page<Parcel> findByCourierIdOrderByCreatedAtDesc(String courierId, Pageable pageable);

    Page<Parcel> findByCourierIdAndStatusOrderByCreatedAtDesc(String courierId, String status, Pageable pageable);

    @Query("SELECT p FROM Parcel p WHERE " +
           "(CAST(:status AS string) IS NULL OR LOWER(p.status) = LOWER(CAST(:status AS string))) AND " +
           "(CAST(:query AS string) IS NULL OR " +
           "LOWER(p.trackingNumber) LIKE LOWER(CONCAT('%', CAST(:query AS string), '%')) " +
           "OR LOWER(p.senderName) LIKE LOWER(CONCAT('%', CAST(:query AS string), '%')) " +
           "OR LOWER(p.recipientName) LIKE LOWER(CONCAT('%', CAST(:query AS string), '%')))")
    Page<Parcel> searchParcels(
            @Param("query") String query,
            @Param("status") String status,
            Pageable pageable);

    long countByStatus(String status);

    @Query("SELECT p.status, count(p) FROM Parcel p GROUP BY p.status")
    List<Object[]> countByStatusGroup();

    @Query("SELECT COALESCE(SUM(p.totalFee), 0) FROM Parcel p WHERE p.status <> 'cancelled'")
    Double sumTotalRevenue();

    @Query("SELECT p FROM Parcel p WHERE p.createdAt >= :since ORDER BY p.createdAt ASC")
    List<Parcel> findParcelsCreatedSince(@Param("since") java.time.Instant since);

    @Query("SELECT p.createdAt, p.deliveredAt, p.updatedAt FROM Parcel p WHERE LOWER(p.status) = 'delivered' AND p.createdAt IS NOT NULL")
    List<Object[]> findDeliveredDurations();
}
