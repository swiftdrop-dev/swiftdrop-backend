package com.swiftdrop.dispatch.repository;

import com.swiftdrop.dispatch.entity.DispatchJob;
import com.swiftdrop.dispatch.entity.enums.DispatchStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DispatchJobRepository extends JpaRepository<DispatchJob, UUID> {

    Optional<DispatchJob> findByParcelId(UUID parcelId);

    Optional<DispatchJob> findByTrackingNumber(String trackingNumber);

    boolean existsByParcelId(UUID parcelId);

    Page<DispatchJob> findByStatus(DispatchStatus status, Pageable pageable);

    Page<DispatchJob> findByStatusIn(Collection<DispatchStatus> statuses, Pageable pageable);

    List<DispatchJob> findByCourierIdAndStatusIn(UUID courierId, Collection<DispatchStatus> statuses);

    long countByStatus(DispatchStatus status);
}
