package com.swiftdrop.dispatch.repository;

import com.swiftdrop.dispatch.entity.DispatchZone;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DispatchZoneRepository extends JpaRepository<DispatchZone, UUID> {
    Optional<DispatchZone> findByZoneCode(String zoneCode);

    boolean existsByZoneCode(String zoneCode);

    List<DispatchZone> findByIsActiveTrue();
}
