package com.swiftdrop.parcel.repository;

import com.swiftdrop.parcel.entity.ParcelAssignment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ParcelAssignmentRepository extends JpaRepository<ParcelAssignment, Long> {

    Optional<ParcelAssignment> findByParcelIdAndUnassignedAtIsNull(String parcelId);

    Page<ParcelAssignment> findByCourierIdAndAssignedAtGreaterThanEqualAndAssignedAtLessThanAndUnassignedAtIsNullOrderByAssignedAtAscIdAsc(
            String courierId, Instant startInclusive, Instant endExclusive, Pageable pageable);

    List<ParcelAssignment> findByParcelIdOrderByAssignedAtAscIdAsc(String parcelId);
}
