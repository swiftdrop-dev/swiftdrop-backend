package com.swiftdrop.dispatch.repository;

import com.swiftdrop.dispatch.entity.DispatchJob;
import com.swiftdrop.dispatch.entity.enums.DispatchStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    List<DispatchJob> findByStatusIn(Collection<DispatchStatus> statuses);

    List<DispatchJob> findByCourierIdAndStatusIn(String courierId, Collection<DispatchStatus> statuses);

    boolean existsByCourierIdAndStatusInAndIdNot(String courierId, Collection<DispatchStatus> statuses, UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select j from DispatchJob j where j.id = :id")
    Optional<DispatchJob> findByIdForUpdate(@Param("id") UUID id);

    @Query(
            value = """
                    select j from DispatchJob j
                    left join j.zone z
                    where j.status in :statuses
                      and (:zoneCode is null or z.zoneCode = :zoneCode)
                    """,
            countQuery = """
                    select count(j) from DispatchJob j
                    left join j.zone z
                    where j.status in :statuses
                      and (:zoneCode is null or z.zoneCode = :zoneCode)
                    """
    )
    Page<DispatchJob> findUnassignedByZone(@Param("statuses") Collection<DispatchStatus> statuses,
                                           @Param("zoneCode") String zoneCode,
                                           Pageable pageable);

    long countByStatus(DispatchStatus status);
}
