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
}
