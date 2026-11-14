package com.swiftdrop.parcel.repository;

import com.swiftdrop.parcel.entity.ParcelDeliveryPhotoProof;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface ParcelDeliveryPhotoProofRepository extends JpaRepository<ParcelDeliveryPhotoProof, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select proof from ParcelDeliveryPhotoProof proof where proof.assignmentId = :assignmentId "
            + "and proof.consumedAt is null and proof.invalidatedAt is null")
    Optional<ParcelDeliveryPhotoProof> findActiveByAssignmentIdForUpdate(
            @Param("assignmentId") Long assignmentId);

    @Modifying(flushAutomatically = true)
    @Query("update ParcelDeliveryPhotoProof proof set proof.invalidatedAt = :invalidatedAt "
            + "where proof.assignmentId = :assignmentId and proof.consumedAt is null "
            + "and proof.invalidatedAt is null")
    int invalidateUnconsumedByAssignmentId(@Param("assignmentId") Long assignmentId,
                                          @Param("invalidatedAt") Instant invalidatedAt);
}
