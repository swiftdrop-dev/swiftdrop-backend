package com.swiftdrop.dispatch.repository;

import com.swiftdrop.dispatch.entity.CourierStatusCache;
import com.swiftdrop.dispatch.entity.enums.VehicleType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CourierStatusCacheRepository extends JpaRepository<CourierStatusCache, String> {

    List<CourierStatusCache> findByIsOnlineTrueAndIsAvailableTrue();

    List<CourierStatusCache> findByIsOnlineTrueAndIsAvailableTrueAndVehicleType(VehicleType vehicleType);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CourierStatusCache c where c.courierId = :courierId")
    Optional<CourierStatusCache> findByCourierIdForUpdate(@Param("courierId") String courierId);
}
