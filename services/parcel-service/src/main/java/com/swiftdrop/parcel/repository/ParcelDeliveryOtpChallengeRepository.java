package com.swiftdrop.parcel.repository;

import com.swiftdrop.parcel.entity.ParcelDeliveryOtpChallenge;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ParcelDeliveryOtpChallengeRepository
        extends JpaRepository<ParcelDeliveryOtpChallenge, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ParcelDeliveryOtpChallenge>
    findFirstByParcelIdAndAssignmentIdOrderByCreatedAtDescIdDesc(String parcelId, Long assignmentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select challenge from ParcelDeliveryOtpChallenge challenge "
            + "where challenge.parcelId = :parcelId "
            + "and challenge.invalidatedAt is null "
            + "and challenge.proofConsumedAt is null "
            + "and challenge.attemptCount < 5 "
            + "order by challenge.createdAt asc, challenge.id asc")
    List<ParcelDeliveryOtpChallenge> findUsableByParcelIdForUpdate(@Param("parcelId") String parcelId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select challenge from ParcelDeliveryOtpChallenge challenge "
            + "where challenge.id = :challengeId and challenge.parcelId = :parcelId")
    Optional<ParcelDeliveryOtpChallenge> findByIdAndParcelIdForUpdate(
            @Param("challengeId") UUID challengeId, @Param("parcelId") String parcelId);
}
