package com.swiftdrop.parcel.repository;

import com.swiftdrop.parcel.entity.ParcelAssignment;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ParcelAssignmentRepository extends JpaRepository<ParcelAssignment, Long> {

    Optional<ParcelAssignment> findByParcelIdAndUnassignedAtIsNull(String parcelId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select assignment from ParcelAssignment assignment "
            + "where assignment.parcelId = :parcelId and assignment.unassignedAt is null")
    Optional<ParcelAssignment> findActiveByParcelIdForUpdate(@Param("parcelId") String parcelId);

    Page<ParcelAssignment> findByCourierIdAndAssignedAtGreaterThanEqualAndAssignedAtLessThanAndUnassignedAtIsNullOrderByAssignedAtAscIdAsc(
            String courierId, Instant startInclusive, Instant endExclusive, Pageable pageable);

    List<ParcelAssignment> findByParcelIdOrderByAssignedAtAscIdAsc(String parcelId);
}
