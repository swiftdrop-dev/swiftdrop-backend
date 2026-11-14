package com.swiftdrop.dispatch.repository;

import com.swiftdrop.dispatch.entity.CourierStatusCache;
import com.swiftdrop.dispatch.entity.enums.VehicleType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CourierStatusCacheRepository extends JpaRepository<CourierStatusCache, UUID> {

    List<CourierStatusCache> findByIsOnlineTrueAndIsAvailableTrue();

    List<CourierStatusCache> findByIsOnlineTrueAndIsAvailableTrueAndVehicleType(VehicleType vehicleType);
}
